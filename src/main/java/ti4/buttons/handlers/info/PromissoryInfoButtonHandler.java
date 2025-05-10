package ti4.buttons.handlers.info;

import lombok.experimental.UtilityClass;
import ti4.helpers.PromissoryNoteHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
class PromissoryInfoButtonHandler {

    @ButtonHandler(value = "refreshPNInfo", save = false)
    public static void sendPromissoryNoteInfoLongForm(Game game, Player player) {
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, true);
    }
}
