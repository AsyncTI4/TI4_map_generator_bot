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
        fracture.ships(fracture.ingress, UnitType.Infantry, 8);

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

    private Optional<TacticalPlan> stageTo(String position) {
        return TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.STAGE && plan.target().equals(position))
                .findFirst();
    }

    private void styxFleet(Tile tile) {
        fracture.ships(tile, UnitType.Dreadnought, 3);
        fracture.ships(tile, UnitType.Carrier, 2);
        fracture.ships(tile, UnitType.Infantry, 6);
    }

    // Styx is three moves from every ingress and a moved fleet waits under its token until the next round, so a fleet
    // of
    // dreadnoughts one move from the ingress starts its march by moving onto the ingress.
    @Test
    void aFleetOneMoveFromTheIngressMarchesOntoIt() {
        styxFleet(fracture.board.home);

        assertThat(stageTo(fracture.ingress.getPosition())).isPresent();
    }

    // The neutral units never move, so each step closer to Styx is worth a larger share of the strike it prepares.
    @Test
    void eachStepTowardStyxIsWorthMoreThanTheOneBefore() {
        styxFleet(fracture.board.home);
        double fromHome = stageTo(fracture.ingress.getPosition()).orElseThrow().score();

        fracture = new FractureScenario();
        styxFleet(fracture.ingress);
        double fromIngress = stageTo("frac2").orElseThrow().score();

        fracture = new FractureScenario();
        styxFleet(fracture.fracture("frac2"));
        double fromEgress = stageTo("frac3").orElseThrow().score();

        assertThat(fromIngress).isGreaterThan(fromHome);
        assertThat(fromEgress).isGreaterThan(fromIngress);
    }

    @Test
    void aFleetOnTheEgressMarchesOnTowardStyxRatherThanBack() {
        styxFleet(fracture.fracture("frac2"));

        double onward = stageTo("frac3").orElseThrow().score();

        double back =
                stageTo(fracture.ingress.getPosition()).map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY);

        assertThat(back).isLessThan(onward);
    }

    // A fleet on its way to Styx gives up the steps it has made when it leaves the Fracture for another target, so an
    // attack on Cocytus from the egress is charged the march it abandons.
    @Test
    void anAttackThatPullsAMarchingFleetOffTheRouteIsChargedTheMarch() {
        styxFleet(fracture.fracture("frac2"));
        double raw = TacticalPlanner.explainAttack(fracture.board.test.game, fracture.nekro(), FractureScenario.COCYTUS)
                .orElseThrow()
                .score();

        Optional<TacticalPlan> charged = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.ATTACK && FractureScenario.COCYTUS.equals(plan.target()))
                .findFirst();

        assertThat(charged.map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY))
                .isLessThan(raw);
    }

    // Without an ingress in its own slice a seat reaches the Fracture only through Mecatol Rex or a neighbour's slice,
    // so
    // the march is worth half as much and the seat keeps its usual fleet pool.
    @Test
    void aSeatWithoutAnIngressInItsSliceValuesTheMarchLess() {
        Game game = fracture.board.test.game;
        styxFleet(fracture.ingress);
        double own = stageTo("frac2").orElseThrow().score();
        assertThat(ti4.ai.eval.FractureCampaign.routeShare(game, fracture.nekro()))
                .isEqualTo(1.0);

        Tile foreign =
                fracture.board.test.place("27", ti4.ai.AiTestGame.neighbourOf(DefenseScenario.SARDAKK_HOME_POSITION));
        foreign.addToken(Constants.TOKEN_INGRESS, "space");
        fracture.ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);

        assertThat(ti4.ai.eval.FractureCampaign.routeShare(game, fracture.nekro()))
                .isLessThan(1.0);
        assertThat(stageTo("frac2").map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY))
                .isLessThan(own);
    }

    // Flank Speed gives every ship one more move, so a dreadnought fleet on the ingress covers two steps of the march.
    @Test
    void flankSpeedLetsAFleetOnTheIngressSkipAStep() {
        styxFleet(fracture.ingress);
        assertThat(stageTo("frac3")).isEmpty();

        fracture.nekro().setActionCard("fs1", 401);
        Optional<TacticalPlan> faster = stageTo("frac3").or(() -> stageTo("frac5"));

        assertThat(faster).isPresent();
        assertThat(TacticalPlanner.needsFlankSpeed(fracture.board.test.game, fracture.nekro(), faster.get()))
                .isTrue();
    }

    @Test
    void flankSpeedLetsAFleetOnTheEgressAttackStyx() {
        styxFleet(fracture.fracture("frac2"));
        fracture.nekro().setActionCard("fs1", 401);

        Optional<TacticalPlan> attack = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.ATTACK && FractureScenario.STYX.equals(plan.target()))
                .findFirst();

        assertThat(attack).isPresent();
        assertThat(attack.get().landings()).containsKey("styx");
    }

    // Thunder's Edge Warfare places no command token, so a march step taken with it leaves the fleet free to move
    // again.
    @Test
    void warfareCanTakeAStepOfTheMarch() {
        styxFleet(fracture.ingress);

        Optional<TacticalPlan> warfare = TacticalPlanner.bestForWarfare(fracture.board.test.game, fracture.nekro());

        assertThat(warfare.map(TacticalPlan::target))
                .hasValueSatisfying(target -> assertThat(target).isIn("frac2", "frac6"));
    }

    @Test
    void aSeatMarchingOnStyxValuesCarrierTwoMore() {
        double near = ti4.ai.strategy.ResearchPolicy.value(fracture.board.test.game, fracture.nekro(), "cv2");

        fracture.ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        double far = ti4.ai.strategy.ResearchPolicy.value(fracture.board.test.game, fracture.nekro(), "cv2");

        assertThat(near).isGreaterThan(far);
    }

    private Tile planetBesideTheIngress() {
        Game game = fracture.board.test.game;
        String side = ti4.model.MapPositionMapper.getAdjacentTilePositions(fracture.ingress.getPosition()).stream()
                .filter(position -> !"x".equals(position) && game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
        return fracture.board.test.place("25", side);
    }

    private double expansionTo(Tile tile) {
        return TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.EXPAND && plan.target().equals(tile.getPosition()))
                .findFirst()
                .orElseThrow()
                .score();
    }

    // A march toward Styx is remembered across rounds, so a fleet that stepped onto a board ingress is charged the
    // march it would give up when another plan takes its ships away, as it is inside the Fracture.
    @Test
    void aRememberedMarchChargesPlansThatTakeItsFleetElsewhere() {
        styxFleet(fracture.ingress);
        Tile side = planetBesideTheIngress();
        double free = expansionTo(side);

        TacticalPlan step = new TacticalPlan(
                Kind.STAGE,
                fracture.ingress.getPosition(),
                List.of(new TacticalPlan.UnitMove(fracture.board.home.getPosition(), "space", UnitType.Dreadnought, 3)),
                java.util.Map.of(),
                5);
        FractureMarch.record(fracture.board.test.game, fracture.nekro(), step);

        assertThat(FractureMarch.of(fracture.board.test.game, fracture.nekro()))
                .hasValueSatisfying(march -> assertThat(march.position()).isEqualTo(fracture.ingress.getPosition()));
        assertThat(expansionTo(side)).isLessThan(free);
    }

    @Test
    void aMarchIsForgottenOnceItsFleetGoesElsewhere() {
        styxFleet(fracture.ingress);
        Game game = fracture.board.test.game;
        FractureMarch.remember(game, fracture.nekro(), fracture.ingress.getPosition());
        TacticalPlan away = new TacticalPlan(
                Kind.EXPAND,
                fracture.board.home.getPosition(),
                List.of(new TacticalPlan.UnitMove(fracture.ingress.getPosition(), "space", UnitType.Carrier, 1)),
                java.util.Map.of(),
                5);

        FractureMarch.record(game, fracture.nekro(), away);

        assertThat(FractureMarch.of(game, fracture.nekro())).isEmpty();
    }

    // Humans take Styx with carriers full of fighters; a march carries the fighters its ships have room for after six
    // infantry, and they count in the strike it prepares.
    @Test
    void aMarchCarriesFightersThatMakeTheFleetStronger() {
        fracture.ships(fracture.ingress, UnitType.Dreadnought, 2);
        fracture.ships(fracture.ingress, UnitType.Carrier, 2);
        fracture.ships(fracture.ingress, UnitType.Infantry, 4);
        double without = stageTo("frac2").map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY);

        fracture.nekro().addTech("cv2");
        fracture.ships(fracture.ingress, UnitType.Fighter, 8);
        Optional<TacticalPlan> step = stageTo("frac2");

        assertThat(step).isPresent();
        assertThat(step.get().score()).isGreaterThan(without);
        assertThat(step.get().moves()).anyMatch(move -> move.type() == UnitType.Fighter);
    }

    @Test
    void aFleetTooSmallForStyxDoesNotMarchTowardIt() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 1);
        fracture.ships(fracture.board.home, UnitType.Carrier, 1);
        fracture.ships(fracture.board.home, UnitType.Infantry, 4);

        assertThat(stageTo(fracture.ingress.getPosition())).isEmpty();
    }

    // The dock nearest an ingress builds the smallest fleet that beats the guard of Styx (three dreadnoughts and a
    // carrier) ahead of surplus ships, and only while Styx is not the seat's.
    @Test
    void theDockNearAnIngressBuildsAFleetForStyx() {
        fracture.ships(fracture.board.home, UnitType.Carrier, 2);
        fracture.board.test.units(fracture.board.home, "mordaiii", fracture.nekro(), UnitType.Infantry, 4);
        fracture.nekro().setTg(10);
        ProductionPlanner.BuildPlan near =
                ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home);

        fracture.ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        ProductionPlanner.BuildPlan far =
                ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home);

        assertThat(near.units(UnitType.Dreadnought)).isGreaterThan(far.units(UnitType.Dreadnought));
    }

    // Three dreadnoughts and a carrier are four ships in one system, one more than the starting fleet pool allows.
    // The token comes ahead of strategy tokens once the stack at the dock is close to the pool.
    // The humans who took Fracture planets filled their carriers with fighters (Saar struck Styx with nine).
    @Test
    void theDockNearAnIngressFillsItsCarriersWithFighters() {
        fracture.ships(fracture.board.home, UnitType.Carrier, 2);
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 3);
        fracture.board.test.units(fracture.board.home, "mordaiii", fracture.nekro(), UnitType.Infantry, 6);
        fracture.nekro().setTg(3);

        ProductionPlanner.BuildPlan plan =
                ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home);

        assertThat(plan.units(UnitType.Fighter)).isPositive();
    }

    @Test
    void aDockNearAnIngressWantsNoMoreFleetTokensThanTheStartingPool() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 2);
        fracture.nekro().setTacticalCC(3);
        fracture.nekro().setFleetCC(3);
        fracture.nekro().setStrategicCC(0);

        String grown = ti4.ai.nekro.CommandTokenPolicy.poolToGrow(fracture.board.test.game, fracture.nekro());

        assertThat(grown).isNotEqualTo("increase_fleet_cc");
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

    private void cocytusFleetOnTheEgress() {
        fracture.ships("frac2", UnitType.Destroyer, 3);
        fracture.ships("frac2", UnitType.Cruiser, 1);
        fracture.ships("frac2", UnitType.Carrier, 1);
        fracture.ships("frac2", UnitType.Infantry, 4);
    }

    // Mahact's fleet in pbd29015b: it entered the egress in round 3 and took Cocytus with its next action.
    private void mahactFleetOnTheEgress() {
        fracture.ships("frac2", UnitType.Cruiser, 1);
        fracture.ships("frac2", UnitType.Destroyer, 1);
        fracture.ships("frac2", UnitType.Dreadnought, 1);
        fracture.ships("frac2", UnitType.Carrier, 1);
        fracture.ships("frac2", UnitType.Fighter, 1);
        fracture.ships("frac2", UnitType.Infantry, 1);
        fracture.ships("frac2", UnitType.Mech, 1);
    }

    // Neutral units never retaliate or bear a grudge, so a strike on them only has to repay the tactic token it
    // spends, not the margin an attack on a player needs.
    @Test
    void aStrikeOnNeutralUnitsOnlyHasToRepayItsToken() {
        assertThat(TacticalPlanner.attackMinScore(fracture.board.test.game.getNeutral()))
                .isEqualTo(ti4.ai.explore.ExploreValues.COMMAND_TOKEN)
                .isLessThan(TacticalPlanner.ATTACK_MIN_SCORE);
    }

    @Test
    void aFleetOnTheEgressThatBeatsTheGuardTakesCocytusBelowThePlayerBar() {
        cocytusFleetOnTheEgress();

        Optional<TacticalPlan> plan = attackOn(FractureScenario.COCYTUS);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsEntry("cocytus", 4);
        assertThat(plan.get().score()).isLessThan(TacticalPlanner.ATTACK_MIN_SCORE);
    }

    // Taking a planet nobody controls is an expansion with a fight first, so it earns the same early-game tempo as
    // landing on a free planet, on top of the planet, its relic and the win chances.
    @Test
    void aNeutralPlanetEarnsTheTempoOfAnExpansion() {
        cocytusFleetOnTheEgress();
        Game game = fracture.board.test.game;
        Tile cocytus = fracture.fracture(FractureScenario.COCYTUS);
        TacticalPlanner.Context context = new TacticalPlanner.Context(game, fracture.nekro());
        TacticalPlanner.Candidate best = TacticalPlanner.candidatePool(context, cocytus, game.getNeutral()).stream()
                .max(java.util.Comparator.comparingDouble(found -> found.quick().score()))
                .orElseThrow();
        Planet planet = cocytus.getPlanetUnitHolders().getFirst();
        double chance =
                best.estimate().spaceWin() * best.estimate().groundWins().get(planet.getName());

        StrikeValue value = context.offense().evaluate(best.strike(), best.estimate(), best.terms(), true);

        double withoutTempo = chance
                * (ti4.ai.eval.BoardView.planetValue(planet)
                        + ti4.ai.eval.FracturePrize.gain(game, fracture.nekro(), planet));
        assertThat(TacticalPlanner.planetTempo(game)).isPositive();
        assertThat(value.planets())
                .isCloseTo(
                        withoutTempo + chance * TacticalPlanner.planetTempo(game),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    // A fleet that entered the egress to take Cocytus gives that strike up if another plan carries its ground forces
    // back out, so the plan pays for it and the fleet strikes first.
    @Test
    void aFleetOnTheEgressStrikesCocytusBeforeItsGroundForcesLeave() {
        mahactFleetOnTheEgress();

        TacticalPlan top = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro())
                .getFirst();

        assertThat(top.kind()).isEqualTo(Kind.ATTACK);
        assertThat(top.target()).isEqualTo(FractureScenario.COCYTUS);
        assertThat(top.landings()).containsKey("cocytus");
    }

    @Test
    void aPlanThatTakesTheGroundForcesOutOfTheEgressIsChargedTheStrike() {
        mahactFleetOnTheEgress();
        String ingress = fracture.ingress.getPosition();
        TacticalPlan raw = TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), ingress)
                .orElseThrow();

        TacticalPlan charged = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.target().equals(ingress) && plan.kind() == raw.kind())
                .findFirst()
                .orElseThrow();

        assertThat(raw.moves()).anyMatch(move -> "frac2".equals(move.origin()) && move.type() == UnitType.Infantry);
        assertThat(charged.score()).isLessThan(raw.score());
    }

    @Test
    void aFleetOnTheEgressWithoutAStrikeIsNotCharged() {
        fracture.ships("frac2", UnitType.Carrier, 1);
        fracture.ships("frac2", UnitType.Infantry, 2);
        String ingress = fracture.ingress.getPosition();
        TacticalPlan raw = TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), ingress)
                .orElseThrow();

        TacticalPlan ranked = TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.target().equals(ingress) && plan.kind() == raw.kind())
                .findFirst()
                .orElseThrow();

        assertThat(ranked.score()).isEqualTo(raw.score());
    }

    @Test
    void noShipIsSentToScreenAnEmptyFracture() {
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);

        assertThat(TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), "frac3")
                        .filter(plan -> plan.kind() == Kind.POSITION))
                .isEmpty();
    }
}
