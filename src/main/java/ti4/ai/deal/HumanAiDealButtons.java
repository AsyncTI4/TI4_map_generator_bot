package ti4.ai.deal;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class HumanAiDealButtons {

    @ButtonHandler(HumanAiDealMessage.ACCEPT_PREFIX)
    public static void accept(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<HumanAiDeal> deal = answerable(event, game, player, buttonID, HumanAiDealMessage.ACCEPT_PREFIX);
        if (deal.isEmpty()) return;
        Optional<String> failure = HumanAiDealRunner.validate(game, deal.get(), System.currentTimeMillis());
        if (failure.isPresent()) {
            HumanAiDealStore.save(game, deal.get().withStatus(HumanAiDeal.Status.FAILED));
            ButtonHelper.deleteAllButtons(event);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "The offer is off: " + failure.get() + ". Nothing was exchanged.");
            return;
        }
        HumanAiDealStore.save(game, deal.get().withStatus(HumanAiDeal.Status.ACCEPTED));
        ButtonHelper.deleteAllButtons(event);
        HumanAiDealRunner.run(event, game, deal.get());
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " accepted the offer: "
                        + deal.get().purpose());
    }

    @ButtonHandler(HumanAiDealMessage.DENY_PREFIX)
    public static void deny(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Optional<HumanAiDeal> deal = answerable(event, game, player, buttonID, HumanAiDealMessage.DENY_PREFIX);
        if (deal.isEmpty()) return;
        HumanAiDealStore.save(game, deal.get().withStatus(HumanAiDeal.Status.DENIED));
        ButtonHelper.deleteAllButtons(event);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " denied the offer: "
                        + deal.get().purpose());
    }

    private static Optional<HumanAiDeal> answerable(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, String prefix) {
        Optional<HumanAiDeal> deal = HumanAiDealStore.find(game, buttonID.replace(prefix, ""));
        boolean theirs = deal.isPresent()
                && player != null
                && player.getFaction().equals(deal.get().humanFaction());
        if (!theirs) return Optional.empty();
        if (deal.get().status() != HumanAiDeal.Status.PENDING) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This offer is no longer open.");
            return Optional.empty();
        }
        return deal;
    }
}
