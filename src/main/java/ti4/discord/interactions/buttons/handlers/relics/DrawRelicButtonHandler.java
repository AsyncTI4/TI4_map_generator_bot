package ti4.discord.interactions.buttons.handlers.relics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RelicHelper;
import ti4.logging.BotLogger;

@UtilityClass
class DrawRelicButtonHandler {

    @ButtonHandler("drawRelicAtPosition_")
    public static void resolveDrawRelicAtPosition(
            Player player, ButtonInteractionEvent event, Game game, String buttonID) {
        int position = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player) {
            PromissoryNoteHelper.resolvePNPlay("dspnflorChecked", player, game, event);
        }
        RelicHelper.drawRelicAndNotify(player, event, game, position, true);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
