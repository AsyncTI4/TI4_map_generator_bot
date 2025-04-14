package ti4.service.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
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

        Modal blindActivationModal = Modal.create("blindActivation_" + event.getMessageId(), "Activate a blind tile")
            .addActionRow(position)
            .build();

        event.replyModal(blindActivationModal).queue();
    }

    @ModalHandler("blindActivation_")
    public static void doBlindActivation(ModalInteractionEvent event, Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        String origMessageId = event.getModalId().replace("blindActivation_", "");
        String position = event.getValue(Constants.POSITION).getAsString().trim();

        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Position " + position + " is invalid.");
            return;
        }

        String targetPosition = position;
        Tile tile = game.getTileByPosition(targetPosition);

        List<Button> chooseTileButtons = new ArrayList<>();
        chooseTileButtons.add(Buttons.green(finChecker + "ringTile_" + targetPosition, tile.getRepresentationForButtons(game, player)));
        chooseTileButtons.add(Buttons.red("ChooseDifferentDestination", "Get a Different Ring"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Click the tile that you wish to activate.", chooseTileButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue();
    }

    //Remove ring buttons player has no tiles they can activate
    public static void filterRingButtons(List<Button> ringButtons, Player player, Game game) {
        Set<String> visiblePositions = FoWHelper.getTilePositionsToShow(game, player);
        if (!visiblePositions.contains("000")) {
            ringButtons.removeIf(b -> b.getId().contains("ringTile_000"));
        }
        if (Collections.disjoint(visiblePositions, Arrays.asList("tl", "tr", "bl", "br"))) {
            ringButtons.removeIf(b -> b.getId().contains("ring_corners"));
        }
        for (Button button : new ArrayList<>(ringButtons)) {
            if (button.getLabel().startsWith("Ring #")) {
                String ring = button.getLabel().replace("Ring #", "");
                int availableTiles = ButtonHelper.getTileInARing(player, game, "ring_" + ring + "_left").size() 
                    + ButtonHelper.getTileInARing(player, game, "ring_" + ring + "_right").size() - 2;
                if (availableTiles == 0) {
                    ringButtons.remove(button);
                }
            }
        }
    }

    public static void resolveVoidActivation(Player player, Game game) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## Your ships continued their journey into The Void " 
            + MiscEmojis.GravityRift + " never to be seen again...");
        
        Map<String, Integer> unitsGoingToVoid = game.getMovedUnitsFromCurrentActivation();
        float valueOfUnitsLost = 0f;
        String unitEmojis = "";
        for (Entry<String, Integer> unit : unitsGoingToVoid.entrySet()) {
            UnitModel model = Mapper.getUnit(unit.getKey());
            if (model != null) {
                valueOfUnitsLost += model.getCost() * unit.getValue();
                unitEmojis += StringUtils.repeat("" + model.getUnitEmoji(), unit.getValue());
            }
        }
        GMService.sendMessageToGMChannel(game, player.getRepresentation(true, false) 
            + " lost " + unitEmojis + " (" + valueOfUnitsLost + " res) to The Void round " + game.getRound() + " turn " + player.getNumberOfTurns(), true);
        game.resetCurrentMovedUnitsFrom1TacticalAction();
    }

}
