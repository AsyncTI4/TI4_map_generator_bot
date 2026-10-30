package ti4.ai.endurance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.Stall;
import ti4.testUtils.BaseTi4Test;

// Prove Endurance is scored by passing last, so the seat that holds it counts how many actions each player still has
// and stalls only while it can outlast the others.
class EnduranceTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        test.nekro.setSecret(Endurance.SECRET);
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(2);
        test.sol.setTacticalCC(1);
    }

    @Test
    void countsTacticTokensAndUnplayedStrategyCards() {
        test.nekro.addSC(1);
        test.nekro.addSC(5);
        test.game.setSCPlayed(1, true);

        assertThat(StallCount.of(test.game, test.nekro, test.nekro)).isEqualTo(4.0);
        assertThat(StallCount.of(test.game, test.sol, test.nekro)).isEqualTo(1.0);
    }

    @Test
    void countsReadyActionTechnologiesOfAnotherPlayerFromTheirPublicCards() {
        test.sol.addTech("sr");

        assertThat(StallCount.of(test.game, test.sol, test.nekro)).isEqualTo(2.0);

        test.sol.exhaustTech("sr");
        assertThat(StallCount.of(test.game, test.sol, test.nekro)).isEqualTo(1.0);
    }

    @Test
    void bioStimsReadiesAnActionTechnologyForAnotherUse() {
        test.sol.addTech("sr");
        test.sol.addTech("bs");

        assertThat(StallCount.of(test.game, test.sol, test.nekro)).isEqualTo(3.0);
    }

    @Test
    void countsItsOwnSlingRelayOnlyWithASpaceDock() {
        test.nekro.addTech("sr");
        double withDock = StallCount.of(test.game, test.nekro, test.nekro);

        test.nekro.addTech("bs");
        assertThat(StallCount.of(test.game, test.nekro, test.nekro)).isEqualTo(withDock + 1);
        assertThat(withDock).isEqualTo(4.0);
    }

    @Test
    void guessesTheComponentCardsInAnotherPlayersHandFromTheDeck() {
        assertThat(StallCount.componentShare(test.game)).isBetween(0.05, 0.6);
    }

    @Test
    void stallsMoreWhenItOutlastsEveryoneStillInTheRound() {
        Endurance.Race race = Endurance.race(test.game, test.nekro).orElseThrow();

        assertThat(race.mine()).isGreaterThan(race.rival());
        assertThat(race.contested()).isTrue();
        assertThat(Endurance.stallBonus(test.game, test.nekro)).isPositive();
        assertThat(Stall.value(test.game, test.nekro)).isGreaterThan(Stall.ACTION_VALUE);
    }

    @Test
    void theCloserTheRivalIsToPassingTheMoreEachStallIsWorth() {
        test.sol.setTacticalCC(3);
        double evenly = Endurance.stallBonus(test.game, test.nekro);
        test.sol.setTacticalCC(0);

        assertThat(Endurance.stallBonus(test.game, test.nekro)).isGreaterThan(evenly);
    }

    @Test
    void doesNotStallAnyHarderWhenItCannotOutlastARival() {
        test.nekro.setTacticalCC(0);
        test.sol.setTacticalCC(6);

        assertThat(Endurance.race(test.game, test.nekro).orElseThrow().contested())
                .isFalse();
        assertThat(Endurance.stallBonus(test.game, test.nekro)).isZero();
        assertThat(Stall.value(test.game, test.nekro)).isEqualTo(Stall.ACTION_VALUE);
    }

    @Test
    void keepsComponentCardsForALaterRoundWhileItCannotWinTheRace() {
        test.nekro.setTacticalCC(0);
        test.sol.setTacticalCC(6);

        assertThat(Endurance.savesCardsForLater(test.game, test.nekro)).isTrue();
        assertThat(Stall.cardActionValue(test.game, test.nekro)).isZero();
        assertThat(Stall.actionValue(test.game, test.nekro)).isEqualTo(Stall.ACTION_VALUE);
    }

    @Test
    void spendsComponentCardsAsStallsOnceItCanWinTheRace() {
        assertThat(Endurance.savesCardsForLater(test.game, test.nekro)).isFalse();
        assertThat(Stall.cardActionValue(test.game, test.nekro)).isGreaterThan(Stall.ACTION_VALUE);
    }

    @Test
    void noRaceWithoutTheSecretOrOnceEveryoneElseHasPassed() {
        test.sol.setPassed(true);
        assertThat(Endurance.race(test.game, test.nekro)).isEmpty();
        assertThat(Endurance.stallBonus(test.game, test.nekro)).isZero();

        test.sol.setPassed(false);
        test.nekro.removeSecret(test.nekro.getSecrets().get(Endurance.SECRET));
        assertThat(Endurance.race(test.game, test.nekro)).isEmpty();
        assertThat(Stall.cardActionValue(test.game, test.nekro)).isEqualTo(Stall.ACTION_VALUE);
    }

    @Test
    void onlyCountsInTheActionPhase() {
        test.game.setPhaseOfGame("agenda");

        assertThat(Endurance.race(test.game, test.nekro)).isEmpty();
    }

    @Test
    void valuesStallingTechnologiesAndLeadershipOnlyWhileHoldingTheSecret() {
        assertThat(Endurance.researchValue(test.nekro, "sr")).isPositive();
        assertThat(Endurance.researchValue(test.nekro, "bs")).isPositive();
        assertThat(Endurance.researchValue(test.nekro, "gd")).isZero();
        assertThat(Endurance.leadershipValue(test.nekro, 3)).isPositive();

        test.nekro.removeSecret(test.nekro.getSecrets().get(Endurance.SECRET));
        assertThat(Endurance.researchValue(test.nekro, "sr")).isZero();
        assertThat(Endurance.leadershipValue(test.nekro, 3)).isZero();
    }
}
