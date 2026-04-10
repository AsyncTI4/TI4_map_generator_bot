package ti4.factions.arborec;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class ArborecHeroButtonHandler {

    public static List<Button> getHeroButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> poses = new ArrayList<>();
        List<Tile> tiles = new ArrayList<>();
        tiles.addAll(CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Infantry));
        tiles.addAll(CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Mech));
        for (Tile tile : tiles) {
            if (!poses.contains(tile.getPosition())) {
                buttons.add(Buttons.green(
                        "arboHeroBuild_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                poses.add(tile.getPosition());
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler("arboHeroBuild_")
    public static void resolveArboHeroBuild(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        List<Button> buttons = Helper.getAbilityBuildButtons(event, player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " Use the buttons to produce units. ",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
