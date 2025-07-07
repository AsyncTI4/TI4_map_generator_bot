package ti4.service.fow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

/*
  To activate FoW+ mode use /fow fow_options

  * 0b tiles are hidden
  * Adjacent hyperlanes that don't connect to the viewing tile are hidden
  * Can only activate tiles you can see
    * Can activate any other tile with Blind Tile button including tiles without a tile
      -> Will send ships into the Void
  * Other players stats areas are visible only by seeing their HS - PNs don't count
  * To remove a token from the board, you need to see it
  * Prevents looking at explore/relic decks
 */
public class FOWPlusService {
    public static final String VOID_TILEID = "-1";

    private static final String FOWPLUS_EXPLORE_WAVE = "fowplus_wave";
    private static final String FOWPLUS_EXPLORE_VORTEX = "fowplus_vortex";
    private static final String FOWPLUS_EXPLORE_CLARITY = "fowplus_clarity";
    private static final String FOWPLUS_EXPLORE_FRACTURE = "fowplus_fracture";
    private static final String FOWPLUS_EXPLORE_SPOOR = "fowplus_spoor";
    private static final String FOWPLUS_EXPLORE_SACRIFICE = "fowplus_sacrifice";

    public static boolean isActive(Game game) {
        return game.getFowOption(FOWOption.FOW_PLUS);
    }

    //Only allow activating positions player can see
    public static boolean canActivatePosition(String position, Player player, Game game) {
        return !isActive(game) || FoWHelper.getTilePositionsToShow(game, player).contains(position);
    }

    //Hide all 0b tiles from FoW map
    public static boolean hideFogTile(String tileID, String label, Game game) {
        return isActive(game) && tileID.equals("0b") && StringUtils.isEmpty(label);
    }

    public static boolean isVoid(Game game, String position) {
        return isActive(game) && game.getTileByPosition(position) == null;
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
        if (tile == null) {
            tile = voidTile(targetPosition);
        }

        List<Button> chooseTileButtons = new ArrayList<>();
        chooseTileButtons.add(Buttons.green(finChecker + "ringTile_" + targetPosition, tile.getRepresentationForButtons(game, player)));
        chooseTileButtons.add(Buttons.red("ChooseDifferentDestination", "Get a Different Ring"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Click the tile that you wish to activate.", chooseTileButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue();
    }

    //Remove ring buttons player has no tiles they can activate
    public static void filterRingButtons(List<Button> ringButtons, Player player, Game game) {
        Set<String> visiblePositions = FoWHelper.getTilePositionsToShow(game, player);
        Tile centerTile = game.getTileByPosition("000");
        if (!visiblePositions.contains("000") || centerTile != null && centerTile.getTileModel() != null && centerTile.getTileModel().isHyperlane()) {
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

        Map<String, Map<UnitKey, List<Integer>>> unitsGoingToVoid = game.getTacticalActionDisplacement();
        float valueOfUnitsLost = 0f;
        String unitEmojis = "";
        for (var unitHolder : unitsGoingToVoid.values()) {
            for (var unit : unitHolder.entrySet()) {
                int total = unit.getValue().stream().collect(Collectors.summingInt(x -> x));
                UnitModel model = player.getUnitFromUnitKey(unit.getKey());
                if (model != null) {
                    valueOfUnitsLost += model.getCost() * total;
                    unitEmojis += StringUtils.repeat("" + model.getUnitEmoji(), total);
                }
            }
        }

        String message = player.getRepresentationUnfoggedNoPing() + " lost " + valueOfUnitsLost + " resources ";
        message += unitEmojis + " to The Void round " + game.getRound() + " turn " + player.getInRoundTurnCount();
        GMService.logPlayerActivity(game, player, message, null, true);
        game.getTacticalActionDisplacement().clear();
    }

    //If the target position is void or hyperlane that does not connect to tile we are checking from
    public static boolean shouldTraverseAdjacency(Game game, String position, int dirFrom) {
        if (!isActive(game)) return true;

        if (isVoid(game, position)) {
            return false;
        }

        Tile targetTile = game.getTileByPosition(position);
        if (targetTile.getTileModel() != null && targetTile.getTileModel().isHyperlane()) {
            boolean hasHyperlaneConnection = false;
            for (int i = 0; i < 6; i++) {
                List<Boolean> targetHyperlaneData = targetTile.getHyperlaneData(i, game);
                if (targetHyperlaneData != null && !targetHyperlaneData.isEmpty() && targetHyperlaneData.get(dirFrom)) {
                    hasHyperlaneConnection = true;
                    break;
                }
            }
            return hasHyperlaneConnection;
        }

        return true;
    }

    //Can only remove CCs from tiles that can be seen
    public static boolean preventRemovingCCFromTile(Game game, Player player, Tile tile) {
        return isActive(game) && !FoWHelper.getTilePositionsToShow(game, player).contains(tile.getPosition());
    }

    //Hide explore and relic decks
    public static boolean deckInfoAvailable(Player player, Game game) {
        if (!isActive(game) || game.getPlayersWithGMRole().contains(player)) return true;

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Deck info not available in FoW+ mode");
        return false;
    }

    //FOWPlus specific explores
    public static void resolveExplore(GenericInteractionCreateEvent event, String exploreCardId, Tile tile, String planetID, Player player, Game game) {
        if (!isActive(game)) return;

        switch (exploreCardId) {
            case FOWPLUS_EXPLORE_WAVE:
                List<Button> waveButtons = new ArrayList<>();
                for (String adjacentPos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
                    Tile adjacentTile = game.getTileByPosition(adjacentPos);
                    if (!adjacentTile.getTileModel().isHyperlane()) {
                        waveButtons.add(Buttons.green("fowplus_wave_" + adjacentPos + "_" + tile.getPosition(),
                            adjacentTile.getRepresentationForButtons(game, player)));
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " Choose a system to eject your non-infantry units", waveButtons);
                break;

            case FOWPLUS_EXPLORE_VORTEX:
                AddTokenCommand.addToken(event, tile, "vortex", game);
                FoWHelper.pingSystem(game, tile.getPosition(), "Space warps unnaturally.");
                int nonCarriedFF = ButtonHelper.checkFleetAndCapacity(player, game, tile, false, false)[4];
                if (nonCarriedFF > 0) {
                    RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), UnitType.Fighter, nonCarriedFF);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() 
                        + " You lost " + nonCarriedFF + " " + UnitEmojis.fighter + " Fighters to the Vortex.");
                } 
                break;

            case FOWPLUS_EXPLORE_CLARITY:
                RemoveCommandCounterService.fromTile(event, player, tile);
                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", use the button to gain one command token.",
                    Buttons.green(player.finChecker() + "redistributeCCButtons_deleteThisMessage","Gain Command Token"));
                break;

            case FOWPLUS_EXPLORE_FRACTURE:
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Use `/move_units tile_name:" 
                    + tile.getPosition() + " unit_names:cv tile_name_to:" 
                    + tile.getPosition() + " unit_names_to:2 cr` to fracture a Carrier into 2 Cruisers for example.");
                break;

            case FOWPLUS_EXPLORE_SPOOR:
                List<Button> buttons = ButtonHelperActionCards.getPlagiarizeButtons(game, player);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), 
                    !buttons.isEmpty() ? "Select the technology you wish to gain." : "No valid technologies to gain.", buttons);
                break;

            case FOWPLUS_EXPLORE_SACRIFICE:
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " Use buttons to resolve Founder's Sacrifice", 
                    Arrays.asList(
                        Buttons.green("winnuStructure_sd_" + planetID,
                            "Place 1 space dock on " + Helper.getPlanetRepresentation(planetID, game), UnitEmojis.spacedock),
                        Buttons.gray("fowplus_sacrifice_" + planetID,
                            "Destroy ground forces on " + Helper.getPlanetRepresentation(planetID, game)),
                        Buttons.red("deleteButtons", "Done Resolving")));
                break;
        }
    }

    @ButtonHandler("fowplus_sacrifice_")
    public static void resolveSacrificeExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetID = buttonID.replace("fowplus_sacrifice_", "");
        Tile tile = game.getTileFromPlanet(planetID);
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planetID);
        int mechs = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
        int infs = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
        
        if (mechs > 0) RemoveUnitService.removeUnit(event, tile, game, player, unitHolder, UnitType.Mech, mechs);
        if (infs > 0) RemoveUnitService.removeUnit(event, tile, game, player, unitHolder, UnitType.Infantry, infs);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "Destroyed all ground forces " + StringUtils.repeat(UnitEmojis.infantry.toString(), infs) + StringUtils.repeat(UnitEmojis.mech.toString(), mechs) 
            + " on " + Helper.getPlanetRepresentation(planetID, game));
    }

    @ButtonHandler("fowplus_wave_")
    public static void resolveWaveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] positions = buttonID.replace("fowplus_wave_", "").split("_");
        String targetPos = positions[0];
        String currentPos = positions[1];

        Tile currentTile = game.getTileByPosition(currentPos);
        Tile targetTile = game.getTileByPosition(targetPos);
        UnitHolder space = currentTile.getSpaceUnitHolder();
        int infs = space.getUnitCount(UnitType.Infantry, player.getColor());
        if (infs > 0) RemoveUnitService.removeUnit(event, currentTile, game, player, space, UnitType.Infantry, infs);

        String unitList = space.getPlayersUnitListOnHolder(player);
        List<RemovedUnit> removed = RemoveUnitService.removeUnits(event, currentTile, game, player.getColor(), unitList, false);
        AddUnitService.addUnits(event, targetTile, game, player.getColor(), unitList, removed);
        StartCombatService.combatCheck(game, event, targetTile);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
            + " Units ejected to " + targetPos + " due to Gravity Wave."
            + (infs > 0 ? " " + infs + " " + StringUtils.repeat(UnitEmojis.infantry.toString(), infs) + " was left behind." : ""));

        if (!currentTile.isGravityRift()) {
            AddTokenCommand.addToken(event, currentTile, "gravityrift", game);
        }
        
        FoWHelper.pingSystem(game, currentPos, "Gravity phenomenon detected.");
        event.getMessage().delete().queue();
    }

}
