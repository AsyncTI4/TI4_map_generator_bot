package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/** Pure bookkeeping behind the passive 30k/JMSC relics: cost reduction, token counter and production parsing. */
class ThirtyKRelicHooksTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;
    private Player xxcha;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("thirtyk-relic-hooks-test");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
        xxcha = addPlayer("xxcha-id", "xxcha", "red");
    }

    @Test
    void sovereignRefugeesDiscountsOnlyFlagshipProduction() {
        sol.addRelic(ThirtyKRelics.SOVEREIGN_REFUGEES);
        sol.produceUnit("dd_101_space");
        assertThat(ThirtyKRelicHooks.applyProductionDiscount(sol, 5)).isEqualTo(5);

        sol.produceUnit("fs_101_space");
        assertThat(ThirtyKRelicHooks.applyProductionDiscount(sol, 9)).isEqualTo(7);
        assertThat(ThirtyKRelicHooks.applyProductionDiscount(sol, 1)).isZero();
        // No relic, no discount.
        hacan.produceUnit("fs_101_space");
        assertThat(ThirtyKRelicHooks.applyProductionDiscount(hacan, 9)).isEqualTo(9);
    }

    @Test
    void overpopulationOnlyLooksAtInfantryOnPlanets() {
        Map<String, Integer> produced = new LinkedHashMap<>();
        produced.put("gf_101_jord", 2);
        produced.put("gf_101_space", 1);
        produced.put("mf_101_jord", 1);
        produced.put("gf_202_arc prime", 1);

        assertThat(ThirtyKRelicHooks.infantryProductionPlanets(produced)).containsExactly("jord", "arc prime");
    }

    @Test
    void socialDissonanceGivesOwnerOnePerTokenAndGiversOne() {
        sol.addRelic(ThirtyKRelics.SOCIAL_DISSONANCE);
        ThirtyKRelicHooks.setSocialDissonanceTokens(game, List.of("hacan", "hacan", "xxcha"));

        assertThat(ThirtyKRelicHooks.commodityBonus(game, sol)).isEqualTo(3);
        assertThat(ThirtyKRelicHooks.commodityBonus(game, hacan)).isEqualTo(1);
        assertThat(ThirtyKRelicHooks.commodityBonus(game, xxcha)).isEqualTo(1);
        assertThat(game.getStoredValue(ThirtyKRelicHooks.SOCIAL_DISSONANCE_KEY)).doesNotContain(",", ":");
    }

    @Test
    void socialDissonanceTokensDoNothingWithoutAnOwner() {
        ThirtyKRelicHooks.setSocialDissonanceTokens(game, List.of("hacan"));
        assertThat(ThirtyKRelicHooks.commodityBonus(game, hacan)).isZero();
    }

    @Test
    void socialDissonanceOverflowsAboveThreeTokens() {
        assertThat(ThirtyKRelicHooks.socialDissonanceOverCapacity(List.of("a", "b", "c")))
                .isFalse();
        assertThat(ThirtyKRelicHooks.socialDissonanceOverCapacity(List.of("a", "b", "c", "d")))
                .isTrue();
    }

    @Test
    void deepNetworksNeedsTheRelicAndTheHoldersTurn() {
        game.setActivePlayerID(sol.getUserID());
        assertThat(ThirtyKRelicHooks.deepNetworksAllows(game, sol, hacan)).isFalse();
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
