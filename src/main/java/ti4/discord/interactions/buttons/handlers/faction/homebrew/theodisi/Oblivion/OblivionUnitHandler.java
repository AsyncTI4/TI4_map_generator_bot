package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class OblivionUnitHandler {

    public static void doOblivionMechCheck(Game game, Player player) {
        if (!player.hasUnit("oblivion_mech")) {
            return;
        }

        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild");
        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may place 1 mech from your reinforcements on a planet you control.",
                buttons);
    }
}
