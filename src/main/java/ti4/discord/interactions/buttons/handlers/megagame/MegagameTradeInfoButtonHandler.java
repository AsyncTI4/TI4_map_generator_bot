package ti4.discord.interactions.buttons.handlers.megagame;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;
import ti4.service.megagame.MegagameTradeInfoService;

@UtilityClass
class MegagameTradeInfoButtonHandler {

    @ButtonHandler(value = MegagameTradeInfoService.OPEN_BUTTON, save = false)
    static void openPicker(ButtonInteractionEvent event, Game game) {
        if (!MegagameTradeInfoService.isAvailable(game)) return;
        MegagameTradeInfoService.sendFactionPicker(event.getHook(), game);
    }

    @SelectionHandler(value = MegagameTradeInfoService.PICK_SELECT, save = false)
    static void showPicked(StringSelectInteractionEvent event, Game game, Player player) {
        if (!MegagameTradeInfoService.isAvailable(game) || event.getValues().isEmpty()) return;
        Player target = game.getPlayerFromColorOrFaction(event.getValues().getFirst());
        if (target == null) return;
        MegagameTradeInfoService.sendPrivateTradeInfo(event.getHook(), game, player, target);
    }

    @ButtonHandler(MegagameTradeInfoService.TRADE_WITH)
    static void tradeWith(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target =
                game.getPlayerFromColorOrFaction(buttonID.substring(MegagameTradeInfoService.TRADE_WITH.length()));
        if (target == null) return;
        if (player == null || !player.isRealPlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Only players in this game can trade.");
            return;
        }
        if (player == target) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You can't trade with yourself.");
            return;
        }
        if (TransactionHelper.hiddenAgendaBlocksTransactions(game)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, player.getRepresentationNoPing() + TransactionHelper.HIDDEN_AGENDA_REFUSAL);
            return;
        }
        TransactionHelper.startTransaction(game, player, target, player.getCardsInfoThread(), true);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                "Your trade with " + target.getRepresentationNoPing() + " is ready in "
                        + player.getCardsInfoThread().getAsMention() + ".");
    }
}
