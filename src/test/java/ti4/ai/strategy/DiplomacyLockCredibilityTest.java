package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.DefenseOutlook;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Locking a system with Diplomacy is only worth the card against a threat a neighbour would really carry out: the
// defence model weighs the enemy's odds, what it risks (ships, the command token) and what it would gain. Merely being
// within reach of an enemy ship is not enough.
class DiplomacyLockCredibilityTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final String HOME_POSITION = "316";
    private static final String BESIDE_HOME = "315";

    private AiTestGame test;
    private Player letnev;
    private Player naalu;
    private Tile home;
    private long modified = 1;

    // Round 2, Letnev active and speaker, holding Diplomacy, with 2 destroyers and a space dock at home.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        letnev = test.addSeat("7100000555555555", "letnev", "red");
        naalu = test.addSeat("200000000000000077", "naalu", "yellow");
        test.game.setRound(2);
        test.game.setVp(10);
        test.isActive(letnev, "action");
        home = test.place("10", HOME_POSITION);
        letnev.addPlanet("arcprime");
        letnev.addPlanet("wrenterra");
        test.units(home, "arcprime", letnev, UnitType.Spacedock, 1);
        test.units(home, "space", letnev, UnitType.Destroyer, 2);
        letnev.addSC(DIPLOMACY);
        test.game.setSpeakerUserID(letnev.getUserID());
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private Tile enemyAt(String tileId, String position) {
        Tile tile = test.place(tileId, position);
        changed();
        return tile;
    }

    private void usersBoard() {
        Tile blocker = test.place("26", "317");
        test.units(blocker, "space", letnev, UnitType.Destroyer, 1);
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Destroyer, 1);
        Tile beyond = enemyAt("28", "314");
        test.units(beyond, "space", naalu, UnitType.Destroyer, 1);
        test.units(beyond, "space", naalu, UnitType.Cruiser, 1);
        changed();
    }

    private PlanInputs inputs() {
        return PlanInputs.of(test.game, letnev, PlanMoment.ACTION);
    }

    private double lockCost() {
        return new DefenseOutlook(test.game, letnev).lockCost(home);
    }

    private AiTurnContext context() {
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_letnev_tacticalAction", "FFCC_letnev_strategicAction_2");
        return test.contextFor(letnev, Set.of(), NOW, turn);
    }

    private boolean diplomacyDueNow() {
        return StrategyCardTurn.dueNow(context(), DIPLOMACY);
    }

    // The reported board: reachable by three Naalu ships, yet the model sees no loss worth a card.
    @Test
    void doesNotLockHomeAgainstTheThreeNaaluShipsOfTheReportedBoard() {
        usersBoard();

        assertThat(inputs().isThreatened(home)).isTrue();
        assertThat(lockCost()).isLessThan(CardValueScale.MIN_LOCK_EXPOSURE);
        assertThat(DiplomacyUses.bestProtection(inputs())).isEmpty();
        assertThat(diplomacyDueNow()).isFalse();
    }

    @Test
    void doesNotLockHomeAgainstALoneDestroyerNextToIt() {
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Destroyer, 1);
        changed();

        assertThat(inputs().isThreatened(home)).isTrue();
        assertThat(DiplomacyUses.bestProtection(inputs())).isEmpty();
        assertThat(diplomacyDueNow()).isFalse();
    }

    @Test
    void doesNotLockHomeAgainstALoneDestroyerEvenWhenTheLeaderIsNearWinning() {
        test.points(letnev, 8);
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Destroyer, 1);
        changed();

        assertThat(letnev.getTotalVictoryPoints()).isEqualTo(8);
        assertThat(DiplomacyUses.bestProtection(inputs())).isEmpty();
        assertThat(diplomacyDueNow()).isFalse();
    }

    @Test
    void locksHomeAgainstALargeFleetWithGroundForcesAtTheDoor() {
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Carrier, 2);
        test.units(beside, "space", naalu, UnitType.Cruiser, 2);
        test.units(beside, "space", naalu, UnitType.Dreadnought, 2);
        test.units(beside, "space", naalu, UnitType.Infantry, 6);
        changed();

        assertThat(lockCost()).isGreaterThanOrEqualTo(CardValueScale.MIN_LOCK_EXPOSURE);
        assertThat(DiplomacyUses.bestProtection(inputs()))
                .hasValueSatisfying(protect -> assertThat(protect.target()).isEqualTo(HOME_POSITION));
        assertThat(diplomacyDueNow()).isTrue();
    }

    // A landing party much smaller than the big fleet still matters when taking home would end the game.
    @Test
    void locksHomeAgainstALandingPartyAtTheDoorOfASeatNearWinning() {
        test.points(letnev, 8);
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Carrier, 1);
        test.units(beside, "space", naalu, UnitType.Cruiser, 1);
        test.units(beside, "space", naalu, UnitType.Infantry, 4);
        changed();

        assertThat(DiplomacyUses.bestProtection(inputs())).map(Benefit::target).contains(HOME_POSITION);
        assertThat(diplomacyDueNow()).isTrue();
    }

    @Test
    void anUndefendedHomeBesideALandingPartyIsLocked() {
        test.game.getTileByPosition(HOME_POSITION).removeAllUnits(letnev.getColor());
        Tile beside = enemyAt("27", BESIDE_HOME);
        test.units(beside, "space", naalu, UnitType.Carrier, 2);
        test.units(beside, "space", naalu, UnitType.Infantry, 6);
        changed();

        assertThat(DiplomacyUses.bestProtection(inputs())).isPresent();
    }
}
