package ti4.discord.interactions.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PromissoryNoteHelper;

@UtilityClass
class PromissoryInfoButtonHandler {

    @ButtonHandler(value = "refreshPNInfo", save = false)
    public static void sendPromissoryNoteInfoLongForm(Game game, Player player) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, true);
    }
}
