package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

/**
 * Guards for the fog side of the offer/accept transaction model: the accept-time cover check, the
 * observer summary redaction, and the button ids / picker lengths the viewer's client receives.
 *
 * <p>The game has no map, so under fog nobody can see anybody's home system - every other player is
 * "hidden" unless an alliance makes them visible. That is exactly the blind case these tests need.
 */
class TransactionHelperFogTest extends BaseTi4Test {

    private Game game;
    private Player red;
    private Player blue;
    private Player green;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("test-fog-transactions");
        game.setFowMode(true);
        red = addPlayer("red-user", "sol", "red");
        blue = addPlayer("blue-user", "hacan", "blue");
        green = addPlayer("green-user", "xxcha", "green");
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    // Offers are always stored on the offerer, in "sending<faction>_receiving<faction>_<thing>_<detail>" form.
    private void offer(Player sender, Player receiver, String thing, String detail) {
        red.addTransactionItem(
                "sending" + sender.getFaction() + "_receiving" + receiver.getFaction() + "_" + thing + "_" + detail);
    }

    // ---- useNewTransactionModel -------------------------------------------------------------

    @Test
    void fogGame_usesNewModelOnlyWhenOptionEnabled() {
        game.setNewTransactionMethod(true);
        assertThat(TransactionHelper.useNewTransactionModel(game)).isFalse();

        game.setFowOption(FOWOption.NEW_TRANSACTIONS, true);
        assertThat(TransactionHelper.useNewTransactionModel(game)).isTrue();
    }

    @Test
    void nonFogGame_followsTransactionMethodFlag() {
        game.setFowMode(false);
        game.setNewTransactionMethod(false);
        assertThat(TransactionHelper.useNewTransactionModel(game)).isFalse();

        game.setNewTransactionMethod(true);
        assertThat(TransactionHelper.useNewTransactionModel(game)).isTrue();
    }

    // ---- findUncoverableItems ---------------------------------------------------------------

    @Test
    void tradeGoodShortfall_isAttributedToTheSender() {
        blue.setTg(3);
        offer(blue, red, "TGs", "4");

        List<TransactionHelper.Shortfall> shortfalls = TransactionHelper.findUncoverableItems(red, blue, false);

        assertThat(shortfalls).hasSize(1);
        assertThat(shortfalls.getFirst().sender()).isSameAs(blue);
    }

    @Test
    void commoditiesShortfall_coveredByTradeGoods_isAccepted() {
        // Existing send rule: missing commodities are made up from trade goods.
        blue.setCommodities(1);
        blue.setTg(2);
        offer(blue, red, "Comms", "3");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, false)).isEmpty();
    }

    @Test
    void tradeGoodsAndCommodities_drawOnTheSameTradeGoodPool() {
        blue.setCommodities(0);
        blue.setTg(3);
        offer(blue, red, "TGs", "2");
        offer(blue, red, "Comms", "2");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, false)).hasSize(1);
    }

    @Test
    void offererSideShortfall_isAttributedToTheOfferer() {
        red.setTg(0);
        blue.setTg(10);
        offer(red, blue, "TGs", "2");
        offer(blue, red, "TGs", "5");

        List<TransactionHelper.Shortfall> shortfalls = TransactionHelper.findUncoverableItems(red, blue, false);

        assertThat(shortfalls).extracting(TransactionHelper.Shortfall::sender).containsExactly(red);
    }

    @Test
    void fragmentShortfall_isReported() {
        offer(blue, red, "Frags", "CRF2");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, false)).hasSize(1);
    }

    @Test
    void genericActionCards_withoutAnyWayToTradeThem_areRefused() {
        blue.setActionCard("sabo1");
        offer(blue, red, "ACs", "generic1");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, false))
                .extracting(TransactionHelper.Shortfall::description)
                .anyMatch(description -> description.contains("neither of you is able to trade"));
    }

    @Test
    void genericActionCards_underBlackMarket_areAccepted() {
        blue.setActionCard("sabo1");
        offer(blue, red, "ACs", "generic1");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, true)).isEmpty();
    }

    @Test
    void genericActionCards_withArbitersOnEitherSide_areAccepted() {
        blue.setActionCard("sabo1");
        red.addAbility("arbiters");
        offer(blue, red, "ACs", "generic1");

        assertThat(TransactionHelper.findUncoverableItems(red, blue, false)).isEmpty();
    }

    // ---- secret objective request picker ------------------------------------------------------

    @Test
    void secretRequestPicker_isSizedByTheHolderNotTheRequester() {
        // Regression: the picker used the requester's own count, so a requester with no secrets
        // got no buttons even when the holder had some.
        blue.setSecret("mine");
        blue.setSecret("mtm");

        assertThat(TransactionHelper.secretRequestPickerLimit(blue, true)).isEqualTo(2);
        assertThat(red.getSecretsUnscored()).isEmpty();
    }

    @Test
    void secretRequestPicker_whenBlind_ignoresTheHoldersCount() {
        blue.setSecret("mine");

        assertThat(TransactionHelper.secretRequestPickerLimit(blue, false))
                .isEqualTo(TransactionHelper.secretRequestPickerLimit(red, false));
    }

    // ---- observer summary -------------------------------------------------------------------

    @Test
    void observerSummary_hidesTheInvisibleSideBehindCategoryCounts() {
        // Green is allied with red, so can read red's sheet but not blue's.
        green.addAllianceMember(red.getFaction());
        offer(red, blue, "TGs", "2");
        offer(blue, red, "Planets", "jord");
        offer(blue, red, "details", "Iwillnotattackyou");

        String summary = TransactionHelper.buildObserverSummary(red, blue, game, green);

        assertThat(summary).contains("Someone gives:");
        assertThat(summary).contains("planets ×1").contains("deal terms ×1");
        assertThat(summary).doesNotContain("jord").doesNotContain("Iwillnotattackyou");
    }

    @Test
    void observerSummary_redactsDealTermsEvenOnTheVisibleSide() {
        green.addAllianceMember(red.getFaction());
        offer(red, blue, "details", "secretfin777plan");

        String summary = TransactionHelper.buildObserverSummary(red, blue, game, green);

        assertThat(summary).contains("[REDACTED]").doesNotContain("secret");
    }

    // ---- buttons the viewer's client receives -----------------------------------------------

    @Test
    void newModelButtonIds_carryColorsNotFactions() {
        List<Button> buttons = TransactionHelper.getStuffToTransButtonsNew(game, red, red, blue);
        buttons.addAll(TransactionHelper.getStuffToTransButtonsNew(game, red, blue, red));

        assertThat(buttons).isNotEmpty();
        assertThat(buttons)
                .extracting(Button::getCustomId)
                .noneMatch(id -> id.contains(blue.getFaction()) || id.contains(red.getFaction()));
    }

    @Test
    void blindRequestCategories_doNotDependOnTheHiddenPlayersHoldings() {
        blue.setTg(0);
        List<String> poor = customIds(TransactionHelper.getStuffToTransButtonsNew(game, red, blue, red));

        blue.setTg(15);
        blue.setCommodities(4);
        List<String> rich = customIds(TransactionHelper.getStuffToTransButtonsNew(game, red, blue, red));

        assertThat(rich).isEqualTo(poor);
    }

    private static List<String> customIds(List<Button> buttons) {
        return buttons.stream().map(Button::getCustomId).toList();
    }
}
