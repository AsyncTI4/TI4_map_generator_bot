package ti4.buttons.handlers.explore;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RelicHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

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
        event.getMessage().delete().queue();
    }
}
