package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A mistyped count (e.g. 1314000146 infantry in pbd26171) once ran the render and button handlers out of memory.
// These ceilings sit far above anything a real game reaches, so they only ever catch bad input.
class SanityLimitsTest extends BaseTi4Test {

    private Game game;
    private Player red;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("sanity-limits-test");
        red = game.addPlayer("red-user", "Red");
        red.setColor("red");
    }

    @Test
    void commandTokenPoolsAreCapped() {
        red.setTacticalCC(1_000_000);
        red.setFleetCC(1_000_000);
        red.setStrategicCC(1_000_000);

        assertThat(red.getTacticalCC()).isEqualTo(SanityLimits.COMMAND_TOKENS_PER_POOL);
        assertThat(red.getFleetCC()).isEqualTo(SanityLimits.COMMAND_TOKENS_PER_POOL);
        assertThat(red.getStrategicCC()).isEqualTo(SanityLimits.COMMAND_TOKENS_PER_POOL);
    }

    @Test
    void raisedCommandTokenLimitsStillFit() {
        // Games may raise the command token limit (ccLimit stored value, Endurance Steroids).
        red.setTacticalCC(22);

        assertThat(red.getTacticalCC()).isEqualTo(22);
    }

    @Test
    void tradeGoodsAreCappedWithoutOverflowing() {
        red.setTg(5);
        red.gainTG(Integer.MAX_VALUE);

        assertThat(red.getTg()).isEqualTo(SanityLimits.TRADE_GOODS);
    }

    @Test
    void debtTokensAreCappedWhenAddedAndLoaded() {
        red.addDebtTokens("blue", 50);
        red.addDebtTokens("blue", Integer.MAX_VALUE);
        assertThat(red.getDebtTokens()).containsEntry("blue", SanityLimits.DEBT_TOKENS_PER_COLOR);

        Map<String, Integer> loaded = new LinkedHashMap<>();
        loaded.put("green", 1_000_000);
        red.setDebtTokens(loaded);
        assertThat(red.getDebtTokens()).containsEntry("green", SanityLimits.DEBT_TOKENS_PER_COLOR);
    }

    @Test
    void stasisUnitsAreCapped() {
        red.setStasisInfantry(1_000_000);
        red.setStasisFighters(1_000_000);

        assertThat(red.getStasisInfantry()).isEqualTo(SanityLimits.STASIS_UNITS);
        assertThat(red.getStasisFighters()).isEqualTo(SanityLimits.STASIS_UNITS);
    }

    @Test
    void leaderTradeGoodsAreCapped() {
        Leader leader = new Leader("nomadagentartuno");
        leader.setTgCount(1_000_000);

        assertThat(leader.getTgCount()).isEqualTo(SanityLimits.TRADE_GOODS_ON_CARD);
    }

    @Test
    void capClampsToZeroAndMax() {
        assertThat(SanityLimits.cap(5, 10)).isEqualTo(5);
        assertThat(SanityLimits.cap(10, 10)).isEqualTo(10);
        assertThat(SanityLimits.cap(11, 10)).isEqualTo(10);
        assertThat(SanityLimits.cap(Integer.MAX_VALUE, 10)).isEqualTo(10);
        assertThat(SanityLimits.cap(0, 10)).isZero();
        assertThat(SanityLimits.cap(-3, 10)).isZero();
        assertThat(SanityLimits.cap(Integer.MIN_VALUE, 10)).isZero();
    }

    @Test
    void cappedSumClampsToZeroAndMax() {
        assertThat(SanityLimits.cappedSum(4, 5, 10)).isEqualTo(9);
        assertThat(SanityLimits.cappedSum(4, 6, 10)).isEqualTo(10);
        assertThat(SanityLimits.cappedSum(4, 7, 10)).isEqualTo(10);
        assertThat(SanityLimits.cappedSum(4, -3, 10)).isEqualTo(1);
        assertThat(SanityLimits.cappedSum(4, -5, 10)).isZero();
    }

    @Test
    void cappedSumDoesNotOverflow() {
        assertThat(SanityLimits.cappedSum(Integer.MAX_VALUE, Integer.MAX_VALUE, 10))
                .isEqualTo(10);
        assertThat(SanityLimits.cappedSum(Integer.MIN_VALUE, Integer.MIN_VALUE, 10))
                .isZero();
    }

    @Test
    void onlyInfantryAndFightersAreTokenBacked() {
        assertThat(SanityLimits.isTokenBacked(UnitType.Infantry)).isTrue();
        assertThat(SanityLimits.isTokenBacked(UnitType.Fighter)).isTrue();
        assertThat(SanityLimits.isTokenBacked(UnitType.Mech)).isFalse();
        assertThat(SanityLimits.isTokenBacked(UnitType.Dreadnought)).isFalse();
        assertThat(SanityLimits.isTokenBacked(UnitType.Spacedock)).isFalse();
    }

    @Test
    void tokenBackedUnitsHaveTheHigherHolderLimit() {
        assertThat(SanityLimits.unitsInOneHolder(UnitType.Infantry))
                .isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
        assertThat(SanityLimits.unitsInOneHolder(UnitType.Fighter))
                .isEqualTo(SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE);
        assertThat(SanityLimits.unitsInOneHolder(UnitType.Dreadnought)).isEqualTo(SanityLimits.PLASTIC_UNITS);
        assertThat(SanityLimits.unitsInOneHolder(UnitType.Mech)).isEqualTo(SanityLimits.PLASTIC_UNITS);
    }

    @Test
    void reinforcementLimitFollowsTheOwnersUnitCapUpToThePlasticLimit() {
        red.setFaction(game, "letnev");
        UnitKey dreadnought = Units.getUnitKey(UnitType.Dreadnought, red.getColorID());

        red.setUnitCap("dn", 5);
        assertThat(SanityLimits.reinforcementLimit(game, dreadnought)).isEqualTo(5);

        red.setUnitCap("dn", 500);
        assertThat(SanityLimits.reinforcementLimit(game, dreadnought)).isEqualTo(SanityLimits.PLASTIC_UNITS);

        red.setUnitCap("dn", 0);
        assertThat(SanityLimits.reinforcementLimit(game, dreadnought)).isZero();
    }

    @Test
    void reinforcementLimitWithoutAnOwnerIsThePlasticLimit() {
        UnitKey unowned = Units.getUnitKey(UnitType.Dreadnought, "ylw");

        assertThat(SanityLimits.reinforcementLimit(game, unowned)).isEqualTo(SanityLimits.PLASTIC_UNITS);
    }

    @Test
    void negativeCommandTokenCountsAreIgnored() {
        red.setTacticalCC(7);
        red.setTacticalCC(-1);

        assertThat(red.getTacticalCC()).isEqualTo(7);
    }

    @Test
    void negativeStasisAndDebtCountsFloorAtZero() {
        red.setStasisInfantry(-5);
        assertThat(red.getStasisInfantry()).isZero();

        red.addDebtTokens("blue", 3);
        red.addDebtTokens("blue", -10);
        assertThat(red.getDebtTokens()).containsEntry("blue", 0);
    }
}
