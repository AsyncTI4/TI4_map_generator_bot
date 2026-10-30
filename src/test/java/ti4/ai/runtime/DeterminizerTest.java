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
