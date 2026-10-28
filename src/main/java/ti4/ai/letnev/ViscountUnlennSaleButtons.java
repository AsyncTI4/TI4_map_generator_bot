package ti4.ai.letnev;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealRunner;
import ti4.ai.tactical.CombatRules;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class ViscountUnlennSaleButtons {

    private static final String SPACE = "space";
    private static final String SEPARATOR = "_";
    private static final int OFFER_PARTS = 4;

    private record Offer(String position, int round, Player buyer) {}

    @ButtonHandler(ViscountUnlennSale.BUY_PREFIX)
    public static void buy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<Offer> offer = offerOf(player, buttonID);
        if (offer.isEmpty()) return;
        Optional<Player> seller = game.getRealPlayers().stream()
                .filter(ViscountUnlennRules::ownsReadyAgent)
                .findFirst();
        if (seller.isEmpty() || !stillBeforeTheRoll(game, offer.get())) {
            ButtonHelper.deleteAllButtons(event);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Viscount Unlenn is no longer on offer for this combat round.");
            return;
        }
        HumanAiDeal deal = saleTo(game, seller.get(), offer.get());
        Optional<String> failure = HumanAiDealRunner.validate(game, deal, System.currentTimeMillis());
        if (failure.isPresent()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The sale is off: " + failure.get() + ".");
            return;
        }
        ButtonHelper.deleteAllButtons(event);
        HumanAiDealRunner.run(event, game, deal);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " bought Viscount Unlenn from "
                        + seller.get().getFactionEmojiOrColor() + " for " + ViscountUnlennSale.PRICE
                        + " trade good.");
    }

    @ButtonHandler(ViscountUnlennSale.DECLINE_PREFIX)
    public static void decline(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<Offer> offer = offerOf(player, buttonID);
        if (offer.isEmpty()) return;
        ButtonHelper.deleteButtonsWithPartialID(
                event,
                SEPARATOR
                        + ViscountUnlennSale.suffix(
                                offer.get().position(), offer.get().round(), player));
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmojiOrColor() + " does not want Viscount Unlenn.");
    }

    private static Optional<Offer> offerOf(Player player, String buttonID) {
        String[] parts = buttonID.split(SEPARATOR);
        boolean theirs = player != null
                && parts.length == OFFER_PARTS
                && StringUtils.isNumeric(parts[2])
                && player.getFaction().equals(parts[3]);
        return theirs ? Optional.of(new Offer(parts[1], Integer.parseInt(parts[2]), player)) : Optional.empty();
    }

    private static boolean stillBeforeTheRoll(Game game, Offer offer) {
        return CombatRules.tracker(game, offer.buyer().getFaction(), offer.position(), SPACE) == offer.round();
    }

    private static HumanAiDeal saleTo(Game game, Player seller, Offer offer) {
        String buyer = offer.buyer().getFaction();
        return new HumanAiDeal(
                Long.toString(System.currentTimeMillis(), Character.MAX_RADIX),
                seller.getFaction(),
                buyer,
                "Viscount Unlenn for " + ViscountUnlennSale.PRICE + " trade good",
                game.getRound(),
                System.currentTimeMillis() + ViscountUnlennSale.PATIENCE_MILLIS,
                HumanAiDeal.Status.PENDING,
                "",
                "",
                List.of(
                        HumanAiDeal.Step.exhaust(
                                ViscountUnlennRules.AGENT,
                                buyer,
                                0,
                                "uses Viscount Unlenn to give one of your ships an extra die"),
                        HumanAiDeal.Step.transfer(
                                buyer, seller.getFaction(), HumanAiDeal.Item.TRADE_GOODS, ViscountUnlennSale.PRICE)));
    }
}
