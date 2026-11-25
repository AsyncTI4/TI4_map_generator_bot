package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.FractureCampaign;
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

    // A cruiser and a carrier are far short of the fleet that takes Styx, but every ship gathered raises the chance of
    // the strike it builds toward (user ruling 2026-11-21), so the step toward Styx earns a little: less than the same
    // step by the taking fleet. The small step may still fall below the bar the ranking keeps (MIN_SCORE).
    @Test
    void aSmallFleetStagesForLessThanTheTakingFleet() {
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);
        Optional<TacticalPlan> small = bestStyxStage();

        fracture = new FractureScenario();
        styxFleet(fracture.ingress);
        Optional<TacticalPlan> taking = bestStyxStage();

        assertThat(small).isPresent();
        assertThat(small.get().score())
                .isPositive()
                .isLessThan(taking.orElseThrow().score());
    }

    private Optional<TacticalPlan> bestStyxStage() {
        Game game = fracture.board.test.game;
        Player seat = fracture.nekro();
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Player neutral = game.getNeutral();
        TacticalPlanner.Context context = new TacticalPlanner.Context(game, seat);
        Optional<TacticalPlan> best = Optional.empty();
        for (Tile staging : game.getTileMap().values()) {
            if (staging == styx || staging == seat.getHomeSystemTile()) continue;
            if (!TacticalPlanner.activatable(game, seat, staging)) continue;
            Optional<TacticalPlan> plan = TacticalPlanner.gather(
                            context, styx, staging, TacticalPlanner.stageLead(neutral, styx))
                    .flatMap(gathering -> TacticalPlanner.stagePlan(
                            context,
                            styx,
                            neutral,
                            gathering.withCounter(TacticalPlanner.counterAt(context, gathering, neutral))));
            if (plan.isPresent()
                    && (best.isEmpty() || plan.get().score() > best.get().score())) best = plan;
        }
        return best;
    }

    // Half the neutral guard of Styx is well beyond a lone carrier, but Styx stays a stage target so the carrier can
    // start gathering toward it.
    @Test
    void aFleetBelowHalfTheGuardStillGetsAStyxStageTarget() {
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);
        fracture.ships(fracture.ingress, UnitType.Infantry, 4);
        TacticalPlanner.Context context = new TacticalPlanner.Context(fracture.board.test.game, fracture.nekro());
        Tile styx = fracture.fracture(FractureScenario.STYX);

        assertThat(TacticalPlanner.stageFeasibility(context, styx, fracture.board.test.game.getNeutral()))
                .isPositive()
                .isLessThan(0.5);
        assertThat(TacticalPlanner.stageTargets(context)).contains(styx);
        assertThat(bestStyxStage()).isPresent();
    }

    // Gravity Drive gives one ship a move of two from frac2 next round, so the dreadnoughts reach Styx in one
    // activation instead of two: the same step onto frac2 prepares a strike that comes sooner.
    @Test
    void gravityDriveMakesTheSameStepWorthMore() {
        styxFleet(fracture.ingress);
        fracture.nekro().setFleetCC(5);
        double without = stageTo("frac2").orElseThrow().score();

        fracture = new FractureScenario();
        styxFleet(fracture.ingress);
        fracture.nekro().setFleetCC(5);
        fracture.nekro().addTech("gd");

        assertThat(stageTo("frac2").orElseThrow().score()).isGreaterThan(without);
    }

    // The same stack without ground forces on two systems: each staging system has its own joiners and its own
    // distance, so each gets its own future strike value, not the one computed first.
    @Test
    void twoOriginsWithTheSameStackGetTheirOwnMarchValue() {
        Tile near = fracture.fracture("frac3");
        Tile far = fracture.fracture("frac6");
        for (Tile tile : List.of(near, far)) {
            fracture.ships(tile, UnitType.Dreadnought, 2);
            fracture.ships(tile, UnitType.Carrier, 1);
        }
        TacticalPlanner.Context context = new TacticalPlanner.Context(fracture.board.test.game, fracture.nekro());
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Player neutral = fracture.board.test.game.getNeutral();

        double fromFar = TacticalPlanner.futureScore(context, styx, neutral, far, List.of(), List.of());
        double fromNear = TacticalPlanner.futureScore(context, styx, neutral, near, List.of(), List.of());

        assertThat(fromNear).isNotEqualTo(fromFar);
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

    // Styx is three moves past the ingress, so a fleet of one-move dreadnoughts two systems short of the ingress is six
    // tactical actions away. It still marches: every system it gains on Styx is part of the strike it prepares. The
    // seat already holds the other Fracture planets, so no staging toward them hides the march on Styx.
    @Test
    void aFleetFarFromTheIngressStillMarchesTowardStyx() {
        Game game = fracture.board.test.game;
        List.of("cocytus", "lethe", "phlegethon").forEach(fracture.nekro()::addPlanet);
        Tile far = fracture.board.test.place("47", fracture.board.beyondStaging());
        styxFleet(far);

        List<TacticalPlan> marches = TacticalPlanner.ranked(game, fracture.nekro()).stream()
                .filter(plan -> TacticalPlanner.isMarchStep(game, fracture.nekro(), plan))
                .toList();

        assertThat(marches).isNotEmpty();
        assertThat(marches.getFirst().moves()).anyMatch(move -> move.origin().equals(far.getPosition()));
    }

    // The gain is not only for a staging move: any tactical action that leaves the Styx fleet closer to Styx earns
    // the progress, so an expansion from the dock onto the ingress is worth more than the expansion alone.
    @Test
    void anExpansionThatCarriesTheStyxFleetCloserEarnsTheProgress() {
        Game game = fracture.board.test.game;
        styxFleet(fracture.board.home);
        String ingress = fracture.ingress.getPosition();
        double alone = TacticalPlanner.forTarget(game, fracture.nekro(), ingress)
                .filter(plan -> plan.kind() == Kind.EXPAND)
                .orElseThrow()
                .score();

        double withProgress = expansionTo(fracture.ingress);

        assertThat(withProgress).isGreaterThan(alone);
    }

    @Test
    void anExpansionThatTakesTheStyxFleetAwayFromStyxIsChargedTheMarch() {
        Game game = fracture.board.test.game;
        styxFleet(fracture.board.home);
        Tile away = planetAwayFromTheIngress();
        Optional<TacticalPlan> alone = TacticalPlanner.forTarget(game, fracture.nekro(), away.getPosition())
                .filter(plan -> plan.kind() == Kind.EXPAND);
        assertThat(alone).isPresent();
        assertThat(alone.get().moves())
                .anyMatch(move ->
                        move.origin().equals(fracture.board.home.getPosition()) && move.type() != UnitType.Infantry);

        assertThat(expansionTo(away)).isLessThan(alone.get().score());
    }

    private Tile planetAwayFromTheIngress() {
        Game game = fracture.board.test.game;
        List<String> besideIngress =
                ti4.model.MapPositionMapper.getAdjacentTilePositions(fracture.ingress.getPosition());
        String side = ti4.model.MapPositionMapper.getAdjacentTilePositions(fracture.board.home.getPosition()).stream()
                .filter(position -> !"x".equals(position)
                        && game.getTileByPosition(position) == null
                        && !besideIngress.contains(position))
                .findFirst()
                .orElseThrow();
        return fracture.board.test.place("25", side);
    }

    // A dreadnought and a carrier are too small to take Styx yet, but the march onto the ingress still earns the part
    // of the taking strike they make up, which is less than the taking fleet's own step.
    @Test
    void aFleetTooSmallForStyxMarchesForLessThanTheTakingFleet() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 1);
        fracture.ships(fracture.board.home, UnitType.Carrier, 1);
        fracture.ships(fracture.board.home, UnitType.Infantry, 4);
        Optional<TacticalPlan> small = stageTo(fracture.ingress.getPosition());

        fracture = new FractureScenario();
        styxFleet(fracture.board.home);
        double taking = stageTo(fracture.ingress.getPosition()).orElseThrow().score();

        assertThat(small).isPresent();
        assertThat(small.get().score()).isPositive().isLessThan(taking);
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

    // The guard of Styx (two dreadnoughts, a destroyer, three infantry) beats every fleet of three unupgraded ships,
    // but
    // a pool of three still gathers toward it (user ruling 2026-11-21): the more it gathers, the better its chance once
    // more fleet tokens let the rest join. A fourth fleet token makes the same march worth more.
    @Test
    void aPoolOfThreeStillGathersTowardStyxButLessThanAPoolOfFour() {
        styxFleetAtHome();
        fracture.nekro().setFleetCC(3);
        double three = styxMarchFromTheIngress();

        assertThat(three).isPositive();

        fracture.nekro().setFleetCC(FractureCampaign.TAKING_SHIPS);

        assertThat(styxMarchFromTheIngress()).isGreaterThan(three);
        assertThat(stageTo(fracture.ingress.getPosition())).isPresent();
    }

    private double styxMarchFromTheIngress() {
        Game game = fracture.board.test.game;
        Tile styx = fracture.fracture(FractureScenario.STYX);
        Player neutral = game.getNeutral();
        TacticalPlanner.Context context = new TacticalPlanner.Context(game, fracture.nekro());
        return TacticalPlanner.gather(context, styx, fracture.ingress, TacticalPlanner.stageLead(neutral, styx))
                .map(gathering -> TacticalPlanner.futureScore(
                        context, styx, neutral, fracture.ingress, gathering.ships(), gathering.cargo()))
                .orElse(0.0);
    }

    // In the status phase a seat whose dock is near an ingress in its own slice keeps the four fleet tokens that fleet
    // needs, even before the ships are built: production is capped by the pool, so the token has to come first.
    // Four is only the fleet that takes Styx from its guard; a stronger rival fleet raises it (below).
    @Test
    void aDockNearAnIngressKeepsAFleetTokenForEveryShipOfTheStyxFleet() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 10);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isEqualTo(FractureCampaign.TAKING_SHIPS);
    }

    // Self-play seats held about six tokens after the status phase, and the Styx fleet came after every tactic and
    // strategy level, so it got none. It now comes right after two tactic tokens and one strategy token: seven tokens
    // are enough for the four ships that take Styx, and a short pool still keeps its first tactics and strategy.
    @Test
    void sevenTokensAreEnoughForTheFleetThatTakesStyx() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 7);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isEqualTo(FractureCampaign.TAKING_SHIPS);
        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.TACTIC)).isEqualTo(2);
        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.STRATEGY)).isEqualTo(1);
    }

    @Test
    void aShortPoolKeepsTwoTacticTokensAndOneStrategyTokenBeforeTheStyxFleet() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 5);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.TACTIC)).isEqualTo(2);
        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.STRATEGY)).isEqualTo(1);
        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isEqualTo(2);
    }

    // Only the four ships that take Styx come early; growing the fleet to match a rival stack still waits for the
    // other tactic and strategy levels.
    @Test
    void theFleetThatMatchesARivalWaitsForTheUsualTacticsAndStrategy() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");
        rivalStack(7);

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 8);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isEqualTo(FractureCampaign.TAKING_SHIPS);
    }

    // Round one: from the round two status phase on every seat is pulled to four fleet tokens anyway.
    @Test
    void withoutAnIngressNearItsDocksASeatKeepsItsUsualFleet() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");
        fracture.board.test.game.setRound(1);
        fracture.ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 10);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isLessThan(FractureCampaign.TAKING_SHIPS);
    }

    @Test
    void aSeatHoldingStyxKeepsItsUsualFleet() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");
        fracture.board.test.game.setRound(1);
        Tile styx = fracture.fracture(FractureScenario.STYX);
        fracture.nekro().addPlanet(styx.getPlanetUnitHolders().getFirst().getName());

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 10);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isLessThan(FractureCampaign.TAKING_SHIPS);
    }

    // The Styx fleet has no fixed size: it has to take Styx and then hold it, so it grows to the strongest rival fleet,
    // in fleet tokens and in the dreadnoughts the dock builds.
    @Test
    void aStrongerRivalFleetRaisesTheFleetTokensKeptForStyx() {
        fracture.board.test.game.setPhaseOfGame("statusHomework");
        rivalStack(7);

        java.util.Map<ti4.ai.nekro.CommandTokenPolicy.Pool, Integer> ideal =
                ti4.ai.nekro.CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 16);

        assertThat(ideal.get(ti4.ai.nekro.CommandTokenPolicy.Pool.FLEET)).isEqualTo(7);
    }

    @Test
    void aStrongerRivalFleetMakesTheDockBuildMoreDreadnoughtsForStyx() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 3);
        fracture.ships(fracture.board.home, UnitType.Carrier, 1);
        fracture.board.test.units(fracture.board.home, "mordaiii", fracture.nekro(), UnitType.Infantry, 6);
        fracture.nekro().setFleetCC(8);
        fracture.nekro().setTg(20);
        int alone = ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        rivalStack(7);
        int facingTheRival = ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), fracture.board.home)
                .units(UnitType.Dreadnought);

        assertThat(FractureCampaign.fleetShips(fracture.board.test.game, fracture.nekro()))
                .isEqualTo(7);
        assertThat(facingTheRival).isGreaterThan(alone);
    }

    @Test
    void theStyxFleetCarriesInfantryForEverySeatItHas() {
        rivalStack(7);

        assertThat(FractureCampaign.infantry(fracture.board.test.game, fracture.nekro()))
                .isGreaterThan(FractureCampaign.INFANTRY);
    }

    private void rivalStack(int dreadnoughts) {
        Player sardakk = fracture.board.sardakk;
        sardakk.setFleetCC(dreadnoughts);
        Tile far = fracture.board.test.place("27", "101");
        fracture.board.test.units(far, "space", sardakk, UnitType.Dreadnought, dreadnoughts);
    }

    private void styxFleetAtHome() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, FractureCampaign.DREADNOUGHTS);
        fracture.ships(fracture.board.home, UnitType.Carrier, FractureCampaign.CARRIERS);
        fracture.board.test.units(
                fracture.board.home, "space", fracture.nekro(), UnitType.Infantry, FractureCampaign.INFANTRY);
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

        // The charge can drop the plan out of the ranking altogether.
        java.util.Optional<TacticalPlan> charged =
                TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                        .filter(plan -> plan.target().equals(ingress) && plan.kind() == raw.kind())
                        .findFirst();

        assertThat(raw.moves()).anyMatch(move -> "frac2".equals(move.origin()) && move.type() == UnitType.Infantry);
        assertThat(charged.map(TacticalPlan::score).orElse(Double.NEGATIVE_INFINITY))
                .isLessThan(raw.score());
    }

    // The seat already holds Styx here: a lone carrier on frac2 earns a little march value toward an untaken Styx (user
    // ruling 2026-11-21), and leaving it would be charged that, which is a different charge from the one tested here.
    @Test
    void aFleetOnTheEgressWithoutAStrikeIsNotCharged() {
        fracture.nekro().addPlanet(ti4.ai.eval.FracturePrize.STYX);
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

    // Argent and Mahact moved fleet and ground forces onto the egress together and struck the next action. The strike
    // carries the same ships without a charge for moving them as a group, so neither does the step that prepares it.
    @Test
    void anAssaultGroupOnTheIngressStepsOntoTheEgressWithItsGroundForces() {
        fracture.ships(fracture.ingress, UnitType.Destroyer, 3);
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);
        Planet planet = fracture.ingress.getPlanetUnitHolders().getFirst();
        fracture.nekro().addPlanet(planet.getName());
        fracture.board.test.units(fracture.ingress, planet.getName(), fracture.nekro(), UnitType.Infantry, 4);

        Optional<TacticalPlan> step = stageTo("frac2");

        assertThat(step).isPresent();
        assertThat(step.get().score()).isGreaterThanOrEqualTo(TacticalPlanner.MIN_SCORE);
        assertThat(step.get().moves()).anyMatch(move -> move.type() == UnitType.Infantry);
        assertThat(step.get().moves()).anyMatch(move -> move.type() == UnitType.Destroyer);
    }

    // The neutral units never move, so a strike one step from the egress is as sure next action as it is now; only a
    // rival can spoil it, and the counterstrike at the egress already prices that. The step is worth the strike it
    // prepares less the costs of making it (here only the distance, as nothing threatens the egress).
    @Test
    void aStepOntoTheEgressCountsTheWholeStrikeItPrepares() {
        fracture.ships(fracture.ingress, UnitType.Dreadnought, 1);
        fracture.ships(fracture.ingress, UnitType.Destroyer, 1);
        fracture.ships(fracture.ingress, UnitType.Carrier, 1);
        fracture.ships(fracture.ingress, UnitType.Fighter, 1);
        Planet planet = fracture.ingress.getPlanetUnitHolders().getFirst();
        fracture.nekro().addPlanet(planet.getName());
        fracture.board.test.units(fracture.ingress, planet.getName(), fracture.nekro(), UnitType.Infantry, 4);
        Game game = fracture.board.test.game;
        Player neutral = game.getNeutral();
        Tile cocytus = fracture.fracture(FractureScenario.COCYTUS);
        Tile egress = fracture.fracture("frac2");
        TacticalPlanner.Context context = new TacticalPlanner.Context(game, fracture.nekro());
        TacticalPlanner.Gathering gathering = TacticalPlanner.gather(
                        context, cocytus, egress, TacticalPlanner.stageLead(neutral))
                .orElseThrow();
        double strike =
                TacticalPlanner.futureScore(context, cocytus, neutral, egress, gathering.ships(), gathering.cargo());

        TacticalPlan step = TacticalPlanner.stagePlan(
                        context,
                        cocytus,
                        neutral,
                        gathering.withCounter(TacticalPlanner.counterAt(context, gathering, neutral)))
                .orElseThrow();

        assertThat(gathering.cargo()).isNotEmpty();
        assertThat(strike).isGreaterThan(TacticalPlanner.attackMinScore(neutral));
        assertThat(step.score()).isCloseTo(strike - 0.2, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void noShipIsSentToScreenAnEmptyFracture() {
        fracture.ships(fracture.ingress, UnitType.Cruiser, 1);

        assertThat(TacticalPlanner.forTarget(fracture.board.test.game, fracture.nekro(), "frac3")
                        .filter(plan -> plan.kind() == Kind.POSITION))
                .isEmpty();
    }
}
