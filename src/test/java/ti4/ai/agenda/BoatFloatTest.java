package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The boat float on Articles of War (user's value review, 2026-11-11): every voter but one puts 1 vote For, the
// remaining seat votes Against with enough to win, Against resolves, every For voter collects 3 TG and passes 1 TG to
// the Against seat, and the speaker breaks a tie Against. An all-AI table of low-stake mechs, each at home, on the
// first agenda (each seat keeps a planet back): Hacan (speaker), Jol-Nar (the most castable votes), Mentak and Naalu.
// Nekro cannot vote and Sol holds no planets.
class BoatFloatTest extends BaseTi4Test {

    private static final String ARTICLES = "Law_For/Against_7_articles_war";
    private static final String TIE = "resolveAgendaVote_outcomeTie*_";

    private AiTestGame test;
    private Player hacan;
    private Player jolnar;
    private Player naalu;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        hacan = home("7100000555555555", "hacan", "yellow", "16", "304", "arretze", "hercant", "kamdorn");
        jolnar = home("7100000666666666", "jolnar", "red", "12", "310", "jol", "nar");
        home("7100000777777777", "mentak", "orange", "02", "312", "mollprimus");
        naalu = home("7100000888888888", "naalu", "purple", "09", "314", "maaluuk", "druaa");
        test.game.setVp(10);
        test.game.setStoredValue("agendaCount", "1");
        test.game.setCurrentAgendaInfo(ARTICLES);
        test.game.setSpeakerUserID(hacan.getUserID());
        test.isActive(jolnar, "agendawaiting");
    }

    private Player home(String id, String faction, String color, String tile, String position, String... planets) {
        Player seat = test.addSeat(id, faction, color);
        test.place(tile, position);
        for (String planet : planets) seat.addPlanet(planet);
        return seat;
    }

    private AiTurnContext speakerAtTheTable() {
        return test.contextFor(hacan, Set.of(), NOW, AiTestGame.prompt("turn", PromptSource.PUBLIC, NOW, "noop"));
    }

    // Nobody gains much from the law passing, so the speaker floats the boat. The seat with the most castable votes
    // (Jol-Nar) takes Against and promises each For voter to vote Against; each pays it 1 TG if Against resolves.
    @Test
    void theSpeakerFloatsTheBoatWhenNobodyWantsTheLaw() {
        BoatFloat.arrange(speakerAtTheTable());

        Optional<BoatFloat.Plan> plan = BoatFloat.active(test.game);
        assertThat(plan).isPresent();
        assertThat(plan.get().speaker()).isEqualTo("hacan");
        assertThat(plan.get().against()).isEqualTo("jolnar");
        assertThat(plan.get().members()).containsExactlyInAnyOrder("hacan", "mentak", "naalu");

        List<Promise> promises = PromiseBook.all(test.game);
        assertThat(promises).hasSize(3);
        assertThat(promises).allSatisfy(promise -> {
            assertThat(promise.isPromiser(jolnar)).isTrue();
            assertThat(promise.target()).isEqualTo("against");
            assertThat(promise.price()).isEqualTo(BoatFloat.FLOAT_PRICE);
            assertThat(promise.status()).isEqualTo(Promise.Status.ACCEPTED);
        });
    }

    // Each For voter casts exactly 1 vote For; the Against seat casts enough Against to tie them.
    @Test
    void theMembersCastOneVoteForAndTheAgainstSeatTiesThem() {
        BoatFloat.arrange(speakerAtTheTable());
        test.isActive(naalu, "agendaVoting");

        Optional<AgendaVoting.Ballot> member = AgendaVoting.plan(test.game, naalu);
        assertThat(member.map(AgendaVoting.Ballot::outcome)).contains("for");
        assertThat(member.map(AgendaVoting.Ballot::votes)).contains(1);

        test.isActive(jolnar, "agendaVoting");
        Optional<AgendaVoting.Ballot> against = AgendaVoting.plan(test.game, jolnar);
        assertThat(against.map(AgendaVoting.Ballot::outcome)).contains("against");
        assertThat(against.map(AgendaVoting.Ballot::votes).orElse(0)).isGreaterThanOrEqualTo(3);
    }

    // Settlement: Jol-Nar's Against vote keeps its promises, and once Against resolves each For voter owes it 1 TG
    // (the For voters have just collected 3 TG each from the law's Against effect).
    @Test
    void eachForVoterOwesTheAgainstSeatOneTradeGoodWhenAgainstResolves() {
        BoatFloat.arrange(speakerAtTheTable());
        test.game.setCurrentAgendaVote("for", "yellow_1;orange_1;purple_1");
        test.game.setCurrentAgendaVote("against", "red_3");

        for (Promise promise : PromiseBook.all(test.game)) {
            assertThat(PromisedVotes.verdict(test.game, promise)).contains(Promise.Status.KEPT);
            Promise kept = promise.with(Promise.Status.KEPT);
            test.game.setStoredValue("resolvedAgendaId", kept.agenda());
            test.game.setStoredValue("resolvedAgendaOutcome", "against");
            assertThat(PromisedVotes.settlement(test.game, kept)).contains(Promise.Status.OWED);
            assertThat(kept.isPromiser(jolnar)).isTrue();
            assertThat(kept.price()).isEqualTo(1);
        }
    }

    // If it comes to a tie, the speaker breaks it Against, as agreed.
    @Test
    void theSpeakerBreaksATieAgainst() {
        BoatFloat.arrange(speakerAtTheTable());
        AiPrompt tie = AiTestGame.prompt("tie", PromptSource.PUBLIC, NOW, TIE + "for", TIE + "against");

        assertThat(AgendaPolicy.chooseOutcome(test.game, hacan, tie.enabledButtons())
                        .map(PromptButton::customId))
                .hasValueSatisfying(id -> assertThat(id).endsWith("against"));
    }

    // The float is AI-only for now: with a human voter at the table there is no plan, and only the speaker arranges.
    @Test
    void noFloatWithAHumanAtTheTable() {
        assertThat(BoatFloat.propose(test.game, hacan)).isPresent();
        assertThat(BoatFloat.propose(test.game, jolnar)).isEmpty();

        Player human = test.addSeat("200000000000000003", "letnev", "green");
        test.place("10", "316");
        human.addPlanet("arcprime");
        human.addPlanet("wrenterra");
        assertThat(BoatFloat.propose(test.game, hacan)).isEmpty();
    }
}
