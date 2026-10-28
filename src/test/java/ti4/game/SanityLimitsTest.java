package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.service.emoji.MiscEmojis;
import ti4.testUtils.BaseTi4Test;

// A mistyped count (e.g. 1314000146 infantry in pbd26171) once ran the render and button handlers out of memory.
// These ceilings sit far above anything a real game reaches, so they only ever catch bad input.
class SanityLimitsTest extends BaseTi4Test {

    private Player red;

    @BeforeEach
    void setUp() {
        Game game = new Game();
        game.setName("sanity-limits-test");
        red = new Player("red-user", "Red", game);
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
    void tradeGoodEmojisStopRepeating() {
        String one = MiscEmojis.tg(1);

        assertThat(MiscEmojis.tg(1_000_000)).hasSize(one.length() * SanityLimits.REPEATED_EMOJIS);
    }
}
