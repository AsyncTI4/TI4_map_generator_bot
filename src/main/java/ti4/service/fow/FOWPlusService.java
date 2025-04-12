package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

/*
  * All 0b tiles hidden from the map.
  * Can only activate tiles you can see.  
  * Can activate any other tile with Blind Tile button which allows activating tiles without a tile
    -> Will send ships into the void
 */
public class FOWPlusService {
    public static final String VOID_TILEID = "-1";

    public static boolean isActive(Game game) {
        return game.getFowOption(FOWOption.FOW_PLUS);
    }

    //Only allow activating positions player can see
    public static boolean canActivatePosition(String position, Player player, Game game) {
        return !isActive(game) || !isVoid(game, position) && FoWHelper.getTilePositionsToShow(game, player).contains(position);
    }

    //Hide all 0b tiles from FoW map
    public static boolean hideFogTile(String tileID, String label, Game game) {
        return isActive(game) && tileID.equals("0b") && StringUtils.isEmpty(label);
    }

    //Always show supernovas player has not seen
    public static boolean tileAlwaysVisible(Tile tile, Player player, Game game) {
        return isActive(game) && !player.getFogLabels().keySet().contains(tile.getPosition()) && tile.isSupernova();
    }

    public static boolean isVoid(Game game, String position) {
        return game.getTileByPosition(position).getTileID().equals(VOID_TILEID);
    }

    public static Tile voidTile(String position) {
        return new Tile(VOID_TILEID, position);
    }
  
    @ButtonHandler("blindTileSelection~MDL")
    public static void offerBlindActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        TextInput position = TextInput.create(Constants.POSITION, "Position to activate", TextInputStyle.SHORT)
            .setRequired(true)
            .build();
        /*TextInput direction = TextInput.create(Constants.PRIMARY_TILE_DIRECTION, "Direction to activate", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("N / NE / SE / S / SW / NW")
            .build();*/

        Modal blindActivationModal = Modal.create("blindActivation_" + event.getMessageId(), "Activate a blind tile")
            .addActionRow(position)
            //.addActionRow(direction)
            .build();

        event.replyModal(blindActivationModal).queue();
    }

    @ModalHandler("blindActivation_")
    public static void doBlindActivation(ModalInteractionEvent event, Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        String origMessageId = event.getModalId().replace("blindActivation_", "");
        String position = event.getValue(Constants.POSITION).getAsString().trim();
        //String direction = event.getValue(Constants.PRIMARY_TILE_DIRECTION).getAsString().trim().toUpperCase();

        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Position " + position + " is invalid.");
            return;
        }

        /*int index = List.of("N", "NE", "SE", "S", "SW", "NW").indexOf(direction);
        if (index == -1) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Direction " + direction + " is invalid.");
            return;
        }

        List<String> adjacentPositions = PositionMapper.getAdjacentTilePositions(position);*/
        String targetPosition = position; //adjacentPositions.get(index);
        Tile tile = game.getTileByPosition(targetPosition);

        List<Button> chooseTileButtons = new ArrayList<>();
        chooseTileButtons.add(Buttons.green(finChecker + "ringTile_" + targetPosition, tile.getRepresentationForButtons(game, player)));
        chooseTileButtons.add(Buttons.red("ChooseDifferentDestination", "Get a Different Ring"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Click the tile that you wish to activate.", chooseTileButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue();
    }

}
