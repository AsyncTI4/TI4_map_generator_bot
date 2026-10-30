package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The chance per round of meeting an action secret's condition comes in three tiers. With nothing to go on it is the
// rate human players managed over a whole game; with a ship placed to make it happen it is higher; and where the seat
// can make it happen next turn it is 0.6. The middle tier is calibrated so that five rounds of it add up to roughly
// the share of kept copies that were scored: 1 - (1 - 0.30)^5 = 0.83 for Demonstrate Your Power against 0.70 kept.
class ActionSecretOddsTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private double perRound(String secret) {
        return ActionSecretOdds.perRound(test.game, test.nekro, secret);
    }

    private Tile enemyNextDoor() {
        Tile next = test.place("46", neighbour);
        test.units(next, "space", test.sol, UnitType.Destroyer, 1);
        return next;
    }

    @Test
    void demonstrateYourPowerNeedsAStackOfThreeAndAnEnemyToReach() {
        assertThat(perRound("dyp")).isEqualTo(0.21);

        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        assertThat(perRound("dyp")).isEqualTo(0.21);

        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        assertThat(perRound("dyp")).isEqualTo(0.30);

        enemyNextDoor();
        assertThat(perRound("dyp")).isEqualTo(0.60);
    }

    @Test
    void braveTheVoidWantsShipsInOrBesideAnAnomaly() {
        assertThat(perRound("btv")).isEqualTo(0.09);

        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        Tile rift = test.place("41", neighbour);
        assertThat(perRound("btv")).isEqualTo(0.18);

        test.units(rift, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(perRound("btv")).isEqualTo(0.60);
    }

    @Test
    void unveilFlagshipNeedsTheFlagshipToReachAnEnemy() {
        assertThat(perRound("uf")).isEqualTo(0.10);

        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
        assertThat(perRound("uf")).isEqualTo(0.19);

        enemyNextDoor();
        assertThat(perRound("uf")).isEqualTo(0.60);
    }

    // Nobody out-scores a sole leader, so Spark a Rebellion has nobody to fight.
    @Test
    void sparkARebellionHasNoTargetForTheSoleLeader() {
        assertThat(perRound("sar")).isEqualTo(0.21);

        test.points(test.nekro, 3);
        assertThat(perRound("sar")).isEqualTo(0.08);

        test.points(test.sol, 3);
        assertThat(perRound("sar")).isEqualTo(0.21);
    }

    // A ship our space cannon covers (a neutral one, say, posted by Pirate Contract) is a standing chance each round.
    @Test
    void dustGainsFromAShipUnderOurSpaceCannon() {
        test.nekro.setSecret("ttfd");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
        assertThat(perRound("ttfd")).isEqualTo(0.085);

        Tile next = test.place("46", neighbour);
        test.units(next, "space", test.game.getNeutral(), UnitType.Destroyer, 1);
        assertThat(perRound("ttfd")).isEqualTo(0.085);

        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        assertThat(perRound("ttfd")).isEqualTo(0.30);
    }

    @Test
    void ordinaryActionSecretsGainFromTheirOneBoardCondition() {
        assertThat(perRound("fwp")).isEqualTo(0.06);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        assertThat(perRound("fwp")).isEqualTo(0.13);

        assertThat(perRound("ttfd")).isEqualTo(0.03);
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
        assertThat(perRound("ttfd")).isEqualTo(0.085);

        assertThat(perRound("baf")).isEqualTo(0.05);
        test.sol.addOwnedPromissoryNoteByID("blue_cf");
        test.nekro.setPromissoryNote("blue_cf");
        test.nekro.addPromissoryNoteToPlayArea("blue_cf");
        assertThat(perRound("baf")).isEqualTo(0.125);

        assertThat(perRound("dtgs")).isEqualTo(0.03);
        test.units(home, "space", test.sol, UnitType.Warsun, 1);
        assertThat(perRound("dtgs")).isEqualTo(0.10);
    }

    // An enemy a move away lifts the chance to 0.6 a round, but the lasting chance (what can be counted on in later
    // rounds) never exceeds the ordinary rate for that secret.
    @Test
    void theLastingChanceIsCappedAtTheOrdinaryRate() {
        assertThat(ActionSecretOdds.lastingPerRound(test.game, test.nekro, "dyp"))
                .isEqualTo(0.21);

        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        assertThat(ActionSecretOdds.lastingPerRound(test.game, test.nekro, "dyp"))
                .isEqualTo(0.30);

        enemyNextDoor();
        assertThat(perRound("dyp")).isEqualTo(0.60);
        assertThat(ActionSecretOdds.lastingPerRound(test.game, test.nekro, "dyp"))
                .isEqualTo(0.30);

        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
        assertThat(perRound("uf")).isEqualTo(0.60);
        assertThat(ActionSecretOdds.lastingPerRound(test.game, test.nekro, "uf"))
                .isEqualTo(0.19);
        assertThat(ActionSecretOdds.lastingPerRound(test.game, test.nekro, "fwp"))
                .isEqualTo(0.06);
    }

    @Test
    void anythingElseIsFivePercentARound() {
        assertThat(perRound("survey")).isEqualTo(0.05);
    }

    // With one round left the chance before the game ends is the chance of that round; with five it compounds.
    @Test
    void compoundsOverTheRoundsLeft() {
        assertThat(ActionSecretOdds.beforeGameEnds(test.game, test.nekro, "dyp"))
                .isCloseTo(1 - Math.pow(1 - 0.21, 5), within(1e-9));

        test.points(test.sol, 9);

        assertThat(ActionSecretOdds.beforeGameEnds(test.game, test.nekro, "dyp"))
                .isCloseTo(0.21, within(1e-9));
    }

    // Losing a home planet is rare unless the board sets it up: a planet held in another home that can be taken back,
    // or the seat's own home with an enemy fleet beside it that outweighs the ships guarding it.
    @Test
    void becomeAMartyrRisesWithAForeignHomePlanetHeldOrAnOutmatchedHome() {
        assertThat(perRound("bam")).isEqualTo(0.03);

        Tile next = enemyNextDoor();
        assertThat(perRound("bam")).isEqualTo(0.20);

        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        assertThat(perRound("bam")).isEqualTo(0.03);
        next.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Destroyer, test.sol.getColor()), 1);

        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("jord");
        assertThat(perRound("bam")).isEqualTo(0.30);
    }
}
