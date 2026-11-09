package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// The audit's case: the win race only counted public objectives, so a seat holding a ready status secret thought a
// rival would win first and dropped the reserve for the spend objective that would have won it the game. With
// WIN_RACE_SECRETS the seat counts its own ready status secret (it knows its hand) and keeps a reserve whenever
// keeping it still wins the race.
class WinRaceSecretsTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int TRADE = 5;
    private static final int WARFARE = 6;
    private static final int IMPERIAL = 8;
    private static final String FORM_A_SPY_NETWORK = "fsn";

    private AiTestGame test;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(5);
        test.aiIsActive("action");
        test.nekroHome();
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        tokens(test.nekro, 0, 0);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + (++awards), points));
    }

    private static void tokens(Player player, int tactic, int strategy) {
        player.setTacticalCC(tactic);
        player.setStrategicCC(strategy);
    }

    private void disableForNekro() {
        AiExperiments.disable(test.nekro, Set.of(Experiment.WIN_RACE_SECRETS));
    }

    // Form a Spy Network needs five action cards, which Nekro holds: its secret is ready for the status phase.
    private void nekroHoldsAReadySpyNetwork() {
        test.nekro.setSecret(FORM_A_SPY_NETWORK);
        for (String card : new String[] {"sabo1", "sabo2", "sabo3", "sabo4", "rally"}) test.nekro.setActionCard(card);
    }

    // Nekro on 9 with Diplomacy (initiative 2) scores its ready secret at status before Sol on 9 with Trade
    // (initiative 5) scores Galvanize the People. Sol does not win first.
    private void nekroOnNineWithAReadySecretAheadOfSol() {
        points(test.nekro, 9);
        test.nekro.addSC(DIPLOMACY);
        nekroHoldsAReadySpyNetwork();
        points(test.sol, 9);
        test.sol.addSC(TRADE);
        tokens(test.sol, 3, 3);
    }

    @Test
    void theSeatsOwnReadyStatusSecretWinsTheRace() {
        nekroOnNineWithAReadySecretAheadOfSol();

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }

    @Test
    void withTheExperimentDisabledTheSecretIsIgnored() {
        nekroOnNineWithAReadySecretAheadOfSol();
        disableForNekro();

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
    }

    // A secret that is not ready does not count: Sol still wins first.
    @Test
    void aSecretThatIsNotReadyDoesNotCount() {
        points(test.nekro, 9);
        test.nekro.addSC(DIPLOMACY);
        test.nekro.setSecret(FORM_A_SPY_NETWORK);
        points(test.sol, 9);
        test.sol.addSC(TRADE);
        tokens(test.sol, 3, 3);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
    }

    // Nekro on 9 holds the unplayed Trade card (initiative 5): its primary and the washed commodities fund Erect a
    // Monument at status (see ScoringReserveProjectionTest). Sol on 9 with Warfare (initiative 6) scores Galvanize the
    // People. Without the reserve Nekro cannot score and Sol wins first; with the reserve kept Nekro scores first and
    // wins, so the reserve stays.
    private void nekroWinsOnlyWithItsMonumentReserve() {
        test.game.getRevealedPublicObjectives().put("monument", 1);
        points(test.nekro, 9);
        test.nekro.addSC(TRADE);
        test.nekro.setTg(1);
        test.nekro.setCommoditiesBase(3);
        points(test.sol, 9);
        test.sol.addSC(WARFARE);
        tokens(test.sol, 3, 3);
    }

    @Test
    void theReserveStaysWhenKeepingItWinsTheRace() {
        nekroWinsOnlyWithItsMonumentReserve();

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
        assertThat(WinRace.winsWithReserve(test.game, test.nekro, "monument")).isTrue();
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(
                        reserved -> assertThat(reserved.objectiveId()).isEqualTo("monument"));
    }

    @Test
    void withTheExperimentDisabledTheReserveIsDroppedForAnyEarlierWinner() {
        nekroWinsOnlyWithItsMonumentReserve();
        disableForNekro();

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }

    // When Sol scores before Nekro anyway (Diplomacy, initiative 2), keeping the reserve cannot win: it is dropped.
    @Test
    void theReserveIsDroppedWhenTheSeatLosesEvenWithIt() {
        nekroWinsOnlyWithItsMonumentReserve();
        test.sol.getSCs().clear();
        test.sol.addSC(DIPLOMACY);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
        assertThat(WinRace.winsWithReserve(test.game, test.nekro, "monument")).isFalse();
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }
}
