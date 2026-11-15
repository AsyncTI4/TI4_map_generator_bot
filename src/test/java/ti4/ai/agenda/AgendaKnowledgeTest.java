package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Milestone 6: what a seat legally knows about the agenda deck. Sol plays Politics and puts cards on top; only Sol
// reads that knowledge.
class AgendaKnowledgeTest extends BaseTi4Test {

    private static final Path AI_PACKAGE = Path.of("src", "main", "java", "ti4", "ai");
    private static final Pattern DECK_READS =
            Pattern.compile("\\b(lookAtTopAgenda|lookAtBottomAgenda|getSentAgendas)\\(");
    private static final Set<String> ALLOWED = Set.of("AgendaKnowledge.java", "PlannedPrimaries.java");

    private AiTestGame test;
    private Player sol;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        sol = test.sol;
        test.game.setRound(3);
        test.game.setVp(10);
        test.game.resetAgendas();
    }

    // Two cards placed on top: the one placed last is on top.
    @Test
    void placingOnTopRecordsTheOrder() {
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_war");
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_peace");

        assertThat(AgendaKnowledge.knownTop(test.game, sol)).containsExactly("minister_peace", "minister_war");
        assertThat(AgendaKnowledge.next(test.game, sol)).contains("minister_peace");
        assertThat(AgendaKnowledge.knownTop(test.game, test.nekro)).isEmpty();
    }

    // A card sent to the bottom is no longer known to be on top.
    @Test
    void aCardSentToTheBottomLeavesTheTop() {
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_war");
        AgendaKnowledge.placedOnBottom(test.game, sol, "minister_war");

        assertThat(AgendaKnowledge.knownTop(test.game, sol)).isEmpty();
    }

    // Each reveal takes the top card, so the known list moves down with the deck; a new round forgets it.
    @Test
    void revealsMoveTheKnowledgeAndANewRoundForgetsIt() {
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_war");
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_peace");

        test.game.getAgendas().removeFirst();
        assertThat(AgendaKnowledge.knownTop(test.game, sol)).containsExactly("minister_war");

        test.game.getAgendas().removeFirst();
        assertThat(AgendaKnowledge.knownTop(test.game, sol)).isEmpty();

        AgendaKnowledge.placedOnTop(test.game, sol, "minister_war");
        test.game.setRound(4);
        assertThat(AgendaKnowledge.knownTop(test.game, sol)).isEmpty();
    }

    // User's value table: Committee Formation is worth 2 TG more to a holder who knows a good Elect Player agenda is
    // coming. Only the holder knows; Nekro's model of Sol keeps the round-table value.
    @Test
    void committeeFormationIsWorthMoreWhenAGoodElectionIsKnownToBeNext() {
        test.game.setCurrentAgendaInfo("Law_Elect Player_1_committee");
        double before = own(sol, "sol");
        AgendaKnowledge.placedOnTop(test.game, sol, "minister_war");

        assertThat(ExpertElectionValues.knowsAGoodElectionComing(new AgendaView(test.game, sol, true), sol))
                .isTrue();
        assertThat(own(sol, "sol") - before).isEqualTo(ExpertElectionValues.COMMITTEE_KNOWN_ELECTION);
        assertThat(ExpertElectionValues.knowsAGoodElectionComing(new AgendaView(test.game, sol, false), sol))
                .isFalse();
    }

    // Guard: only AgendaKnowledge (and Politics placement) may look at hidden agenda cards.
    @Test
    void onlyAgendaKnowledgeReadsHiddenAgendaCards() throws IOException {
        try (Stream<Path> files = Files.walk(AI_PACKAGE)) {
            List<String> readers = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !ALLOWED.contains(path.getFileName().toString()))
                    .filter(AgendaKnowledgeTest::readsTheDeck)
                    .map(path -> path.getFileName().toString())
                    .toList();
            assertThat(readers).as("ti4.ai files reading hidden agenda cards").isEmpty();
        }
    }

    private static boolean readsTheDeck(Path path) {
        try {
            return DECK_READS
                    .matcher(Files.readString(path, StandardCharsets.UTF_8))
                    .find();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private double own(Player voter, String outcome) {
        OptionalDouble value = AgendaValues.of(test.game, voter, true, true, "committee", outcome, outcome);
        assertThat(value).isPresent();
        return value.getAsDouble();
    }
}
