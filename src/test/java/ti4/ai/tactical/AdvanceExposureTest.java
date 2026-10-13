package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// An advance parks a carrier and its infantry in an empty system to reach planets next activation. Instead of refusing
// any system an enemy ship can reach, the planner prices the stop at the loss it expects before its next activation:
//   P(attack) x loss if attacked.
// Loss if attacked is the chance the enemy wins the space combat times the cost of what sits in space; when a retreat
// destination is adjacent it is capped by the expected cost of the first round's hits (cheapest units first, sustain
// first), since the group can announce a retreat and leave after one round. P(attack) is the proxy in
// DefenseOutlook.attackLikelihood: the enemy's net gain (our loss minus its own expected losses and the cost of a
// tactical action) as a share of the larger of that gain, its best alternative (the best free planets its carriers can
// reach) and a floor of 3, scaled by its aggression. A group the enemy cannot beat, or whose kill is not worth an
// action, is never attacked.
class AdvanceExposureTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;
    private Tile enemyPost;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.nekro.setTacticalCC(3);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("47", neighbour);
        test.place("27", twoSystemsFromHome());
        enemyPost = test.place("48", besideNeighbourAwayFromHome());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    // Two dreadnoughts and two cruisers (three of them fit Sol's fleet supply) sit next to the empty system. A lone
    // carrier there dies to the first hit (90%), and its 4 resources of ships and infantry are worth an action to Sol,
    // so the expected loss outweighs being one step closer to the planets and the carrier stays home.
    @Test
    void aLoneCarrierDoesNotStopBesideAStrongFleet() {
        strongFleetNextDoor();

        assertThat(advance().filter(plan -> plan.score() >= 0)).isEmpty();
        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
    }

    // The same fleet, but two destroyers can come along. They soak the first round's hits, so the carrier can announce
    // a retreat and leave with its infantry; losing about two destroyers is not worth Sol's action, so the stop is
    // safe.
    @Test
    void destroyersEscortTheCarrierPastAStrongFleet() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 2);
        strongFleetNextDoor();

        TacticalPlan plan = advance().orElseThrow();

        assertThat(moved(plan, UnitType.Destroyer)).isEqualTo(2);
        assertThat(plan.score()).isPositive();
    }

    // A dreadnought and a cruiser are a weaker neighbour: the lone carrier is still a worthwhile kill, but one
    // destroyer escort makes it a group that can retreat, and the escorted carrier advances.
    @Test
    void aDestroyerEscortLetsTheCarrierAdvancePastAWeakerFleet() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 1);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(moved(plan, UnitType.Carrier)).isEqualTo(1);
        assertThat(moved(plan, UnitType.Destroyer)).isEqualTo(1);
    }

    // Without the destroyer the same stop costs more than it gains.
    @Test
    void withoutTheEscortTheWeakerFleetStillDetersALoneCarrier() {
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 1);

        assertThat(advance().filter(plan -> plan.score() >= 0)).isEmpty();
    }

    // A lone destroyer next door wins about half the time against a lone carrier, but the carrier can retreat after the
    // first round and Sol risks its own ship for a small, uncertain kill: not worth an action, so the carrier advances
    // exactly as if no enemy were near.
    @Test
    void aWeakNeighbourDoesNotDeterTheAdvance() {
        double alone = advance().orElseThrow().score();
        test.units(enemyPost, "space", test.sol, UnitType.Destroyer, 1);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(plan.score()).isEqualTo(alone);
    }

    // Two of Nekro's dreadnoughts already hold the empty system. Sol's dreadnought and cruiser cannot contend with
    // them, so the carrier joining them is never attacked: the advance is scored as if Sol were not there.
    @Test
    void backupTheEnemyCannotContendWithRemovesTheRisk() {
        double alone = advance().orElseThrow().score();
        Tile post = test.game.getTileByPosition(neighbour);
        test.units(post, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 1);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 1);

        TacticalPlan plan = advance().orElseThrow();

        assertThat(plan.score()).isEqualTo(alone);
    }

    // The expected loss itself: what Sol's fleet would cost a lone carrier with two infantry, against the same carrier
    // with two destroyers and against the carrier joining three dreadnoughts.
    @Test
    void escortsAndBackupLowerTheExpectedLoss() {
        strongFleetNextDoor();
        Tile post = test.game.getTileByPosition(neighbour);
        DefenseOutlook defense = new DefenseOutlook(test.game, test.nekro);
        Exposure exposure = new Exposure(test.game, test.nekro, defense);
        Defenders lone = defense.defendersAt(post)
                .arriving(List.of(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Infantry, 2)));
        Defenders escorted = lone.arriving(List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 2)));
        Defenders backed = lone.arriving(List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 3)));

        assertThat(exposure.expectedLoss(post, lone)).isGreaterThan(2.0);
        assertThat(exposure.expectedLoss(post, escorted)).isZero();
        assertThat(exposure.expectedLoss(post, backed)).isZero();
    }

    // Expanding leaves the carrier in space beside the fleet too. The planet is still worth taking, so it expands, but
    // the plan is priced at half the carrier's exposure.
    @Test
    void anExpansionBesideAStrongFleetIsPricedButStillTaken() {
        test.place("26", neighbour);
        double alone = expansion().score();
        strongFleetNextDoor();

        TacticalPlan plan = expansion();

        assertThat(plan.landings()).containsKey("lodor");
        assertThat(plan.score()).isLessThan(alone).isGreaterThan(alone - 2.0);
    }

    private TacticalPlan expansion() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                .filter(plan -> plan.kind() == Kind.EXPAND)
                .orElseThrow();
    }

    private void strongFleetNextDoor() {
        test.units(enemyPost, "space", test.sol, UnitType.Dreadnought, 2);
        test.units(enemyPost, "space", test.sol, UnitType.Cruiser, 2);
    }

    private Optional<TacticalPlan> advance() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour).filter(plan -> plan.kind() == Kind.POSITION);
    }

    private static int moved(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private String twoSystemsFromHome() {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }

    private String besideNeighbourAwayFromHome() {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        String goal = twoSystemsFromHome();
        return PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position -> !"x".equals(position)
                        && !AiTestGame.HOME.equals(position)
                        && !nearHome.contains(position)
                        && !goal.equals(position))
                .findFirst()
                .orElseThrow();
    }
}
