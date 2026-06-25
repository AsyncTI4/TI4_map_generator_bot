package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.xan;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class XanAbilityButtonHandler {

    public static void offerQuantumFabrication(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile) {
        String msg = player.getRepresentationUnfogged()
                + ", if you placed this space dock via **Construction**, you may use its PRODUCTION ability immediately in "
                + tile.getRepresentationForButtons(game, player) + " via **Quantum Fabrication**.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                msg,
                Helper.getPlaceUnitButtons(event, player, game, tile, "ministerBuild", "place"));
    }
}
