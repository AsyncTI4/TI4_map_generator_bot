package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.NeutralSetups.Pirates;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class NeutralSetupsTest extends BaseTi4Test {

    private static final String NEBULA = "42";
    private static final String SUPERNOVA = "43";
    private static final String EMPTY = "46";
    private static final String MECATOL = "18";
    private static final String EVERRA = "68";

    private AiTestGame test;
    private Tile home;
    private String next;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.aiIsActive("action");
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(2);
        home = test.nekroHome();
        next = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
    }

    private double contract(Tile target) {
        return NeutralSetups.value(test.game, test.nekro, target, Pirates.CONTRACT);
    }

    private double fleet(Tile target) {
        return NeutralSetups.value(test.game, test.nekro, target, Pirates.FLEET);
    }

    @Test
    void braveTheVoidWantsAnAnomalyTheFleetCanReach() {
        test.nekro.setSecret("btv");

        assertThat(contract(test.place(NEBULA, next))).isPositive();
        assertThat(contract(test.place(EMPTY, next))).isZero();
    }

    @Test
    void withoutAMatchingSecretThereIsNothingToSetUp() {
        assertThat(contract(test.place(NEBULA, next))).isZero();

        test.nekro.setSecret("sar");
        assertThat(contract(test.place(NEBULA, next))).isZero();
    }

    @Test
    void nothingIsWorthSettingUpWithoutATacticTokenForTheAttack() {
        test.nekro.setSecret("btv");
        test.nekro.setTacticalCC(0);

        assertThat(contract(test.place(NEBULA, next))).isZero();
    }

    @Test
    void aSupernovaCannotBeAttackedSoItIsNeverASetup() {
        test.nekro.setSecret("btv");

        assertThat(contract(test.place(SUPERNOVA, next))).isZero();
    }

    @Test
    void aSystemWithOurOwnCommandTokenCannotBeActivatedAgain() {
        test.nekro.setSecret("btv");
        Tile nebula = test.place(NEBULA, next);
        nebula.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(contract(nebula)).isZero();
    }

    @Test
    void aRivalsPlanetKeepsTheNeutralFromBeingTheOnlyOpponent() {
        test.nekro.setSecret("btv");
        Tile everra = test.place(EVERRA, next);
        assertThat(contract(everra)).isPositive();

        test.sol.addPlanet("everra");
        assertThat(contract(everra)).isZero();
    }

    @Test
    void mecatolIsNeverASetup() {
        test.nekro.setSecret("dyp");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);

        assertThat(contract(test.place(EMPTY, next))).isPositive();
        assertThat(contract(test.place(MECATOL, next))).isZero();
    }

    @Test
    void unveilFlagshipNeedsTheFlagshipToReachTheSystem() {
        test.nekro.setSecret("uf");
        Tile target = test.place(EMPTY, next);
        assertThat(contract(target)).isZero();

        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
        assertThat(contract(target)).isPositive();
    }

    @Test
    void demonstrateYourPowerNeedsThreeNonFighterShipsToArrive() {
        test.nekro.setSecret("dyp");
        Tile target = test.place(EMPTY, next);
        assertThat(contract(target)).isZero();

        test.units(home, "space", test.nekro, UnitType.Fighter, 4);
        assertThat(contract(target)).isZero();

        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        assertThat(contract(target)).isPositive();
    }

    @Test
    void theContractIsNotTheCardForFightWithPrecision() {
        test.nekro.setSecret("fwp");
        test.units(home, "space", test.nekro, UnitType.Destroyer, 4);

        assertThat(contract(test.place(EMPTY, next))).isZero();
    }

    @Test
    void thePirateFleetIsWorthPlayingForFightWithPrecisionWhenTheBarrageClearsBothFighters() {
        test.nekro.setSecret("fwp");
        Tile target = test.place(EMPTY, next);
        assertThat(fleet(target)).isZero();

        test.units(home, "space", test.nekro, UnitType.Destroyer, 6);
        assertThat(fleet(target)).isPositive();
    }

    @Test
    void aSetupIsWorthLessThanTheWholePoint() {
        test.nekro.setSecret("btv");

        assertThat(contract(test.place(NEBULA, next))).isLessThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    private Player neutral() {
        return test.game.getNeutral();
    }

    private void neutralDestroyerIn(Tile tile) {
        test.units(tile, "space", neutral(), UnitType.Destroyer, 1);
    }

    @Test
    void anAttackOnTheNeutralDestroyerInAnAnomalyCarriesTheBraveTheVoidBonus() {
        test.nekro.setSecret("btv");
        Tile nebula = test.place(NEBULA, next);
        neutralDestroyerIn(nebula);

        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, next))
                .hasValueSatisfying(plan -> assertThat(plan.kind()).isEqualTo(TacticalPlan.Kind.ATTACK));
    }

    @Test
    void againstNeutralUnitsUnveilFlagshipAndDemonstrateYourPowerAreNotPatient() {
        test.game.setRound(1);
        test.nekro.setSecret("dyp");
        test.nekro.setSecret("uf");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
        Tile target = test.place(EMPTY, next);
        List<UnitMove> ships = List.of(
                new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 3),
                new UnitMove(AiTestGame.HOME, "space", UnitType.Flagship, 1));

        double againstNeutral =
                TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, neutral(), ships, true, false);
        double againstPlayer =
                TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);

        assertThat(againstNeutral).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
        assertThat(againstPlayer).isLessThan(againstNeutral);
    }

    @Test
    void sparkARebellionNeverCountsAgainstTheNeutralPlayer() {
        test.game.setRound(1);
        test.nekro.setSecret("sar");
        Tile target = test.place(EMPTY, next);
        List<UnitMove> ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));

        assertThat(TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, neutral(), ships, true, false))
                .isZero();
        assertThat(TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false))
                .isPositive();
    }

    @Test
    void neutralFightersCountAsFightersToBeBarraged() {
        Tile target = test.place(EMPTY, next);
        test.units(target, "space", neutral(), UnitType.Fighter, 2);

        assertThat(PrecisionRules.enemyFighters(test.game, test.nekro, target)).isEqualTo(2);
    }
}
