package ti4.helpers;

import ti4.map.Game;
import ti4.message.MessageHelper;

public class TIGLHelper {
        public static void sendTIGLSetupText(Game game) {
        game.setCompetitiveTIGLGame(true);
        String message = "# " + Emojis.TIGL + "TIGL\nThis game has been flagged as a Twilight Imperium Global League (TIGL) Game!\n" +
            "Please ensure you have all:\n" +
            "- [Signed up for TIGL](https://forms.gle/QQKWraMyd373GsLN6) - there is no need to confirm your signup was successful\n" +
            "- Read and accepted the TIGL [Code of Conduct](https://discord.com/channels/943410040369479690/1003741148017336360/1155173892734861402)\n" +
            "For more information, please see this channel: https://discord.com/channels/943410040369479690/1003741148017336360\n" +
            "By continuing forward with this game, it is assumed you have accepted and are subject to the TIGL Code of Conduct";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
    }
}
