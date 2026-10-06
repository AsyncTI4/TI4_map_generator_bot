package ti4.discord.interactions.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.listeners.context.ButtonContext;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;

@UtilityClass
class TradeGoodButtonHandler {

    @ButtonHandler("gain_1_tg")
    public static void gain1TG(ButtonContext context) {
        ButtonInteractionEvent event = context.getEvent();
        Game game = context.getGame();
        Player player = context.getPlayer();
        String label = event.getButton().getLabel();

        if (label.contains("inf") && label.contains("mech")) {
            String message = "Please resolve removing infantry manually, if applicable.";
            ReactionService.addReaction(event, game, player, message);
            return;
        }

        String message = "Gained 1 trade good " + player.gainTG(1, true) + ".";
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        ReactionService.addReaction(event, game, player, message);

        ButtonHelper.deleteMessage(event);

        if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
            MessageHelper.sendMessageToChannel(context.getMainGameChannel(), player.getFactionEmoji() + " " + message);
        }
    }

    @ButtonHandler("gain1tgFromLetnevCommander")
    public static void gain1tgFromLetnevCommander(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gain1tgFromMuaatCommander")
    public static void gain1tgFromMuaatCommander(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @Deprecated
    @ButtonHandler("gain1tgFromCommander")
    public static void gain1tgFromCommander(ButtonContext context) {
        Player player = context.getPlayer();
        String message =
                player.getRepresentation() + " gained 1 trade good " + player.gainTG(1) + " from their commander.";
        ButtonHelperAbilities.pillageCheck(player, context.getGame());
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(context.getMainGameChannel(), message);
        ButtonHelper.deleteMessage(context.getEvent());
    }
}
