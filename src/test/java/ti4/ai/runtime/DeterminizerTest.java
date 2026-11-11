package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.RandomHelper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class DeterminizerTest extends BaseTi4Test {

    private AiTestGame test;
    private Game game;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        game = test.game;
        xxcha = test.addSeat("200000000000000002", "xxcha", "green");
        game.setActionCards(new ArrayList<>(Mapper.getDeck("action_cards_pok").getNewDeck()));
        game.setRelics(Mapper.getDeck("relics_pok").getNewDeck());
        game.setAgendas(new ArrayList<>(Mapper.getDeck("agendas_pok").getNewDeck()));
        game.setSecretObjectives(
                new ArrayList<>(Mapper.getDeck("secret_objectives_pok").getNewDeck()));
        for (Player player : List.of(test.nekro, test.sol, xxcha)) {
            for (int i = 0; i < 3; i++) game.drawActionCard(player.getUserID());
            game.drawSecretObjective(player.getUserID());
        }
        game.drawSecretObjective(test.sol.getUserID());
    }

    @AfterEach
    void unseed() {
        RandomHelper.unseedThisThread();
    }

    @Test
    void othersGetFreshHandsOfTheSameSize() {
        // The deciding seat (Nekro) must not plan around the real hands of Sol and Xxcha, so their cards are dealt
        // again.
        Map<String, Integer> nekroCards = new HashMap<>(test.nekro.getActionCards());
        Map<String, Integer> nekroSecrets = new HashMap<>(test.nekro.getSecrets());
        int solCards = test.sol.getActionCards().size();
        int solSecrets = test.sol.getSecretsUnscored().size();
        Set<Integer> solSlots = new HashSet<>(test.sol.getActionCards().values());
        Set<String> cardsInPlay = allActionCards();

        boolean anyChanged = false;
        for (long seed = 1; seed <= 10 && !anyChanged; seed++) {
            Map<String, Integer> before = new HashMap<>(test.sol.getActionCards());
            resample(seed);
            anyChanged = !before.keySet().equals(test.sol.getActionCards().keySet());
        }

        assertThat(anyChanged).as("Sol's hand is resampled").isTrue();
        assertThat(test.nekro.getActionCards()).isEqualTo(nekroCards);
        assertThat(test.nekro.getSecrets()).isEqualTo(nekroSecrets);
        assertThat(test.sol.getActionCards()).hasSize(solCards);
        assertThat(test.sol.getSecretsUnscored()).hasSize(solSecrets);
        assertThat(new HashSet<>(test.sol.getActionCards().values())).isEqualTo(solSlots);
        // No card is lost or duplicated: hands plus deck hold the same cards as before.
        assertThat(allActionCards()).isEqualTo(cardsInPlay);
    }

    @Test
    void theSameSeedDealsTheSameHands() {
        resample(42);
        Set<String> first = new HashSet<>(test.sol.getActionCards().keySet());

        // A fresh table: its setup draws pick hand slot numbers unseeded, so only the cards are compared.
        setUp();
        resample(42);

        assertThat(test.sol.getActionCards().keySet()).isEqualTo(first);
    }

    // Status phases reveal from the peekable list first, so a rollout that leaves it alone knows the next objectives.
    @Test
    void theNextObjectivesToBeRevealedAreResampled() {
        game.setPublicObjectives1(new ArrayList<>(List.of("a", "b", "c", "d", "e")));
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("f", "g", "h", "i", "j")));
        game.setPublicObjectives2(new ArrayList<>(List.of("k", "l", "m", "n", "o")));
        game.setPublicObjectives2Peekable(new ArrayList<>(List.of("p", "q", "r", "s", "t")));
        List<String> firstStageOne = List.copyOf(game.getPublicObjectives1Peekable());
        List<String> firstStageTwo = List.copyOf(game.getPublicObjectives2Peekable());

        resample(7);

        assertThat(game.getPublicObjectives1Peekable()).hasSize(5).isNotEqualTo(firstStageOne);
        assertThat(game.getPublicObjectives2Peekable()).hasSize(5).isNotEqualTo(firstStageTwo);
        assertThat(unrevealed(game.getPublicObjectives1Peekable(), game.getPublicObjectives1()))
                .containsExactlyInAnyOrder("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        assertThat(unrevealed(game.getPublicObjectives2Peekable(), game.getPublicObjectives2()))
                .containsExactlyInAnyOrder("k", "l", "m", "n", "o", "p", "q", "r", "s", "t");
    }

    // Nekro looked at "g", so it may plan around it. Sol looked at "h", which Nekro does not know.
    @Test
    void onlyObjectivesTheDecidingSeatPeekedAtStayInPlace() {
        game.setPublicObjectives1(new ArrayList<>(List.of("a", "b", "c", "d", "e")));
        game.setPublicObjectives1Peekable(new ArrayList<>(List.of("f", "g", "h", "i", "j")));
        game.getPublicObjectives1Peeked().put("g", new ArrayList<>(List.of(test.nekro.getUserID())));
        game.getPublicObjectives1Peeked().put("h", new ArrayList<>(List.of(test.sol.getUserID())));

        boolean solsMoved = false;
        for (long seed = 1; seed <= 10; seed++) {
            resample(seed);
            assertThat(game.getPublicObjectives1Peekable().get(1)).isEqualTo("g");
            solsMoved |= !"h".equals(game.getPublicObjectives1Peekable().get(2));
        }

        assertThat(solsMoved).isTrue();
    }

    private static List<String> unrevealed(List<String> peekable, List<String> deck) {
        List<String> all = new ArrayList<>(peekable);
        all.addAll(deck);
        return all;
    }

    private void resample(long seed) {
        Random random = new Random(seed);
        RandomHelper.useForThisThread(random);
        Determinizer.resample(game, test.nekro, random);
    }

    private Set<String> allActionCards() {
        Set<String> cards = new HashSet<>(game.getActionCards());
        game.getRealPlayers()
                .forEach(player -> cards.addAll(player.getActionCards().keySet()));
        return cards;
    }
}
