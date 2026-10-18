package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.PlanetStake;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Fracture's neutral units as a strike opponent: Cocytus, Styx and Lethe/Phlegethon are prizes (resources, a relic,
// and for Styx a victory point) that a fleet only takes when it can win the fight.
class FractureTargetsTest extends BaseTi4Test {

    private FractureScenario fracture;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
    }

    private Optional<TacticalPlan> attackOn(String position) {
        return TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), position)
                .filter(plan -> plan.kind() == Kind.ATTACK);
    }

    private void bigFleetBesideStyx() {
        fracture.ships("frac3", UnitType.Dreadnought, 5);
        fracture.ships("frac3", UnitType.Carrier, 2);
        fracture.ships("frac3", UnitType.Infantry, 4);
    }

    @Test
    void theNeutralPlayerIsTheOnlyOpponentOnAFractureTile() {
        Tile styx = fracture.fracture(FractureScenario.STYX);

        assertThat(TacticalPlanner.singleOpponent(fracture.board.test.game, fracture.nekro(), styx))
                .isSameAs(fracture.board.test.game.getNeutral());
    }

    @Test
    void aSmallFleetDoesNotAttackStyx() {
        fracture.ships("frac3", UnitType.Cruiser, 1);
        fracture.ships("frac3", UnitType.Carrier, 1);
        fracture.ships("frac3", UnitType.Infantry, 2);

        assertThat(attackOn(FractureScenario.STYX)).isEmpty();
    }

    @Test
    void aFleetThatCanWinAttacksStyx() {
        bigFleetBesideStyx();

        Optional<TacticalPlan> plan = attackOn(FractureScenario.STYX);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey("styx");
    }

    @Test
    void takingStyxIsWorthItsVictoryPoint() {
        bigFleetBesideStyx();

        StrikeValue value = TacticalPlanner.explainAttack(
                        fracture.board.test.game, fracture.nekro(), FractureScenario.STYX)
                .orElseThrow();

        assertThat(value.planets()).isGreaterThan(8.0);
        assertThat(value.anger()).isZero();
        assertThat(value.counter()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void cocytusIsATargetForAModestFleet() {
        fracture.ships("frac2", UnitType.Cruiser, 2);
        fracture.ships("frac2", UnitType.Dreadnought, 2);
        fracture.ships("frac2", UnitType.Carrier, 1);
        fracture.ships("frac2", UnitType.Infantry, 4);

        Optional<TacticalPlan> plan = attackOn(FractureScenario.COCYTUS);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey("cocytus");
    }

    @Test
    void neutralUnitsCarryNoStandingsAndNoGrudge() {
        Standings standings = new DefenseOutlook(fracture.board.test.game, fracture.nekro()).standings();

        assertThat(standings.hurtWeight(fracture.board.test.game.getNeutral())).isZero();
        assertThat(standings.denialWeight(fracture.board.test.game.getNeutral()))
                .isZero();
        assertThat(standings.winThreat(fracture.board.test.game.getNeutral())).isZero();
        assertThat(standings.ready(fracture.board.test.game.getNeutral())).isEmpty();
    }

    @Test
    void planningNeverFailsWithNeutralUnitsOnTheBoard() {
        bigFleetBesideStyx();
        fracture.ships("frac2", UnitType.Cruiser, 2);

        List<TacticalPlan> plans = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro());

        assertThat(plans).isNotNull();
    }

    @Test
    void aHeldStyxIsAGuardedSystem() {
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Planet planet = styx.getPlanetUnitHolders().getFirst();
        fracture.nekro().addPlanet(planet.getName());
        fracture.board.test.units(styx, planet.getName(), fracture.nekro(), UnitType.Infantry, 2);

        DefenseOutlook defense = new DefenseOutlook(fracture.board.test.game, fracture.nekro());

        assertThat(defense.isGuarded(styx)).isTrue();
        assertThat(defense.guardedTiles()).contains(styx);
    }

    @Test
    void aHeldStyxCostsItsVictoryPointToLose() {
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Planet planet = styx.getPlanetUnitHolders().getFirst();
        double before = PlanetStake.of(fracture.board.test.game, fracture.nekro(), planet);
        fracture.nekro().addPlanet(planet.getName());
        fracture.board.test.units(styx, planet.getName(), fracture.nekro(), UnitType.Infantry, 2);

        double held = PlanetStake.of(fracture.board.test.game, fracture.nekro(), planet);

        assertThat(held).isGreaterThanOrEqualTo(before);
        assertThat(held).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void aFleetThatCanWinStyxStagesWithinReachOfIt() {
        fracture.ships(fracture.ingress, UnitType.Dreadnought, 5);
        fracture.ships(fracture.ingress, UnitType.Carrier, 2);
        fracture.ships(fracture.ingress, UnitType.Infantry, 4);

        List<TacticalPlan> stages = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.STAGE)
                .toList();

        assertThat(stages).isNotEmpty();
        assertThat(stages.getFirst().target()).isIn("frac2", "frac3", "frac5", "frac6");
    }

    @Test
    void aSmallFleetDoesNotStageTowardStyx() {
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);

        assertThat(TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                        .filter(plan -> plan.kind() == Kind.STAGE))
                .isEmpty();
    }

    @Test
    void aDockNearAnIngressBuildsDreadnoughtsPastTheUsualThree() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 3);
        fracture.ships(fracture.board.home, UnitType.Carrier, 2);
        fracture.ships(fracture.board.home, UnitType.Flagship, 1);
        fracture.board.test.units(fracture.board.home, "mordaiii", fracture.nekro(), UnitType.Infantry, 5);
        fracture.nekro().setTg(14);
        int near = ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        fracture.ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        int far = ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        assertThat(near).isGreaterThan(far);
    }

    @Test
    void aRivalFleetThatReachesStyxMakesAScreeningShipWorthSending() {
        rivalFleetAtAnotherIngress();
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);

        Optional<TacticalPlan> screen = TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), "frac2")
                .filter(plan -> plan.kind() == Kind.POSITION);

        assertThat(screen).isPresent();
        assertThat(screen.get().moves()).allMatch(move -> move.origin().equals(fracture.ingress.getPosition()));
    }

    @Test
    void aScreenOnOneSideIsWorthLessThanTheScreenThatCompletesTheSeal() {
        rivalFleetAtAnotherIngress();
        Standings standings = new DefenseOutlook(fracture.board.test.game, fracture.nekro()).standings();
        double first = new FractureScreen(fracture.board.test.game, fracture.nekro(), standings)
                .valueOf(fracture.fracture("frac2"));

        fracture.ships("frac6", UnitType.Cruiser, 1);
        double completing = new FractureScreen(fracture.board.test.game, fracture.nekro(), standings)
                .valueOf(fracture.fracture("frac2"));

        assertThat(first).isPositive();
        assertThat(completing).isGreaterThan(first);
    }

    private void scoreMarrow(Player player) {
        Game game = fracture.board.test.game;
        game.scorePublicObjective(player.getUserID(), game.addCustomPO("A Song Like Marrow", 1));
    }

    private void rivalFleetAtAnotherIngress() {
        Player sardakk = fracture.board.sardakk;
        sardakk.addTech("cr2");
        sardakk.addTech("cv2");
        sardakk.addTech("gd");
        sardakk.setFleetCC(12);
        Tile far = fracture.board.test.place("27", "101");
        far.addToken(Constants.TOKEN_INGRESS, "space");
        fracture.board.test.units(far, "space", sardakk, UnitType.Cruiser, 8);
        fracture.board.test.units(far, "space", sardakk, UnitType.Carrier, 1);
        fracture.board.test.units(far, "space", sardakk, UnitType.Infantry, 4);
        fracture.board.test.game.setRound(5);
        fracture.board.test.points(sardakk, 8);
    }

    @Test
    void aRivalsStyxIsAttackedForItsPointAndDeniesTheRival() {
        Player sardakk = fracture.board.sardakk;
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Planet planet = styx.getPlanetUnitHolders().getFirst();
        styx.removeAllUnits(fracture.board.test.game.getNeutral().getColor());
        fracture.board.test.game.getNeutral().removePlanet(planet.getName());
        sardakk.addPlanet(planet.getName());
        fracture.board.test.units(styx, planet.getName(), sardakk, UnitType.Infantry, 2);
        scoreMarrow(sardakk);
        bigFleetBesideStyx();

        StrikeValue value = TacticalPlanner.explainAttack(
                        fracture.board.test.game, fracture.nekro(), FractureScenario.STYX)
                .orElseThrow();

        assertThat(value.fractureDenial()).isPositive();
        assertThat(value.planets()).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE / 2);
    }

    @Test
    void theSongLikeMarrowCountsAsAScoredPointAndNotAsAnUnknownObjective() {
        Player sardakk = fracture.board.sardakk;
        scoreMarrow(sardakk);
        Standings standings = new DefenseOutlook(fracture.board.test.game, fracture.nekro()).standings();

        assertThat(standings.vp(sardakk)).isEqualTo(1);
        assertThat(standings.projected(sardakk)).isGreaterThanOrEqualTo(1.0);
        assertThat(standings.ready(sardakk)).isEmpty();
    }

    @Test
    void noShipIsSentToScreenAnEmptyFracture() {
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);

        assertThat(TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), "frac3")
                        .filter(plan -> plan.kind() == Kind.POSITION))
                .isEmpty();
    }
}
