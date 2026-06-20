package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
class ArmisticeAcd2ButtonHandler {

    @ButtonHandler("resolveArmistice")
    public static void resolveArmistice(Player player, Game game, ButtonInteractionEvent event) {
        Player target = game.getActivePlayer();
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Target player not found.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        String activeSystem = game.getActiveSystem();
        Tile tile = StringUtils.isBlank(activeSystem) ? null : game.getTileByPosition(activeSystem);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Active system not found.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        if (CommandCounterHelper.hasCC(target, tile)) {
            RemoveCommandCounterService.fromTile(target.getColor(), tile, game);
            target.setTacticalCC(target.getTacticalCC() + 1);
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    player.getFactionEmojiOrColor() + " resolved _Armistice_ and removed "
                            + target.getFactionEmojiOrColor()
                            + "'s command token from " + tile.getRepresentationForButtons() + ".");
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    player.getFactionEmojiOrColor() + " resolved _Armistice_. "
                            + target.getFactionEmojiOrColor()
                            + " had no command token in " + tile.getRepresentationForButtons() + " to remove.");
        }

        List<Button> conclusionButtons = new ArrayList<>();
        conclusionButtons.add(ButtonHelper.getEndTurnButton(game, target));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + " your turn has been ended by _Armistice_."
                        + " Use the buttons to resolve \"end of turn\" abilities and then end turn.",
                conclusionButtons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
