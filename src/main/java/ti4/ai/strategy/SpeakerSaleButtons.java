package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.ai.deal.HumanAiDeal;
import ti4.ai.deal.HumanAiDealRunner;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class SpeakerSaleButtons {

    private static final String SEPARATOR = "_";
    private static final int ID_PARTS = 3;

    @ButtonHandler(SpeakerSale.BUY_PREFIX)
    public static void buy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<SpeakerPromise> promise = openOffer(game, player, buttonID);
        if (promise.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This speaker sale is no longer open.");
            return;
        }
        HumanAiDeal payment = payment(game, promise.get());
        Optional<String> failure =
                HumanAiDealRunner.validate(game, payment, promise.get().offeredAt());
        if (failure.isPresent()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The speaker sale is off: " + failure.get() + ".");
            return;
        }
        ButtonHelper.deleteAllButtons(event);
        HumanAiDealRunner.run(event, game, payment);
        promise.get().with(SpeakerPromise.Status.ACCEPTED).save(game);
        Player seller = game.getPlayerFromColorOrFaction(promise.get().seller());
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " bought the speaker token from " + seller.getFactionEmojiOrColor()
                        + " for " + promise.get().price() + " trade goods. In the next strategy phase "
                        + player.getFactionEmojiOrColor() + " picks first but leaves "
                        + seller.getFactionEmojiOrColor() + " the card it names.");
    }

    @ButtonHandler(SpeakerSale.DECLINE_PREFIX)
    public static void decline(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<SpeakerPromise> promise = openOffer(game, player, buttonID);
        ButtonHelper.deleteAllButtons(event);
        if (promise.isEmpty()) return;
        promise.get().with(SpeakerPromise.Status.DECLINED).save(game);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getFactionEmojiOrColor() + " does not want to buy the speaker token.");
    }

    static Optional<SpeakerPromise> openOffer(Game game, Player player, String buttonID) {
        String[] parts = buttonID.split(SEPARATOR, ID_PARTS);
        if (player == null || parts.length != ID_PARTS) return Optional.empty();
        return SpeakerPromise.read(game)
                .filter(promise -> promise.status() == SpeakerPromise.Status.OFFERED)
                .filter(promise -> promise.buyer().equals(player.getFaction()))
                .filter(promise -> String.valueOf(promise.saleRound()).equals(parts[1]))
                .filter(promise -> promise.seller().equals(parts[2]))
                .filter(promise -> game.getPlayerFromColorOrFaction(promise.seller()) != null);
    }

    private static HumanAiDeal payment(Game game, SpeakerPromise promise) {
        return new HumanAiDeal(
                Long.toString(promise.offeredAt(), Character.MAX_RADIX),
                promise.seller(),
                promise.buyer(),
                "The speaker token for " + promise.price() + " trade goods",
                promise.saleRound(),
                promise.expiresAt(),
                HumanAiDeal.Status.PENDING,
                "",
                "",
                List.of(HumanAiDeal.Step.transfer(
                        promise.buyer(), promise.seller(), HumanAiDeal.Item.TRADE_GOODS, promise.price())));
    }
}
