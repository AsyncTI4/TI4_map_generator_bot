package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VyserixBreakthroughHandler {

    public static void offerMoraySystemButtons(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, int hits) {
        if (hits <= 0) return;
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "vyserixMoraySystem_" + tile.getPosition(),
                "Place 1 Fighter in " + tile.getRepresentationForButtons(game, player)));
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String msg = player.getRepresentation() + " you could potentially cancel "
                + (hits == 1 ? "the ANTI-FIGHTER BARRAGE hit" : "some ANTI-FIGHTER BARRAGE hits")
                + " to place fighters instead due to _M.O.R.A.Y. System_. Use these buttons to do so, and press done when done. The bot did not track how many hits you got.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("vyserixMoraySystem_")
    public static void placeMoraySystemFighter(
            Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String pos = buttonID.replace("vyserixMoraySystem_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) return;
        String msg = player.getRepresentation()
                + " canceled one ANTI-FIGHTER BARRAGE hit to place one fighter in "
                + tile.getRepresentationForButtons(game, player) + " due to _M.O.R.A.Y. System_.";
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 ff");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }
}
