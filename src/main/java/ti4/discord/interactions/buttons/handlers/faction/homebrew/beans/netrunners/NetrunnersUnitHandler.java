package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class NetrunnersUnitHandler {

    public static void offerNimdaDeploy(Game game, Player player) {
        if (!player.hasUnit("netrunners_mech")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "mf", "placeOneNDone_skipbuild");
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may deploy a _Nimda_ (Net-Runners mech) to a planet you control.",
                buttons);
    }
}
