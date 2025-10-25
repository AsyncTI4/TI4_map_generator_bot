package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.MantisTileDraftableSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.logging.BotLogger;
import ti4.model.DraftErrataModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TileEmojis;
import ti4.service.milty.MiltyDraftTile;

public class MantisTileDraftable extends Draftable {

    // Used in drafting
    @Getter
    private final List<BlueTileDraftItem> blueTiles = new ArrayList<>();

    @Getter
    private final List<RedTileDraftItem> redTiles = new ArrayList<>();

    @Setter
    private Integer extraBlues;

    @Setter
    private Integer extraReds;

    // Used in map building
    @Getter
    @Setter
    private Integer mulligans;

    @Getter
    private final List<String> discardedTileIDs = new ArrayList<>();

    @Getter
    @Setter
    private String drawnTileId = null;

    @Getter
    private final List<String> mulliganTileIDs = new ArrayList<>();

    public static final DraftableType TYPE = DraftableType.of("MantisTile");
    private static final String ChoiceKeyPrefix = "tile";

    public Category getItemCategory(String choiceKey) {
        if (choiceKey == null) return null;
        String tileID = getItemId(choiceKey);
        if (tileID == null) return null;
        for (BlueTileDraftItem tile : blueTiles) {
            if (tile.ItemId.equals(tileID)) {
                return tile.ItemCategory;
            }
        }
        for (RedTileDraftItem tile : redTiles) {
            if (tile.ItemId.equals(tileID)) {
                return tile.ItemCategory;
            }
        }
        return null;
    }

    public static String getItemId(String choiceKey) {
        if (choiceKey == null) return null;
        if (!choiceKey.startsWith(ChoiceKeyPrefix)) return null;
        return choiceKey.substring(ChoiceKeyPrefix.length());
    }

    public DraftItem getDraftItem(String choiceKey) {
        String tileID = getItemId(choiceKey);
        if (tileID == null) return null;
        for (BlueTileDraftItem tile : blueTiles) {
            if (tile.ItemId.equals(tileID)) {
                return tile;
            }
        }
        for (RedTileDraftItem tile : redTiles) {
            if (tile.ItemId.equals(tileID)) {
                return tile;
            }
        }
        return null;
    }

    public static String makeChoiceKey(String tileId) {
        if (tileId == null) return null;
        return ChoiceKeyPrefix + tileId;
    }

    public int getTotalBluePerPlayer(MapTemplateModel mapTemplate) {
        if (mapTemplate == null) {
            return 0;
        }
        return mapTemplate.bluePerPlayer() + (extraBlues != null ? extraBlues : 0);
    }

    public int getTotalRedPerPlayer(MapTemplateModel mapTemplate) {
        if (mapTemplate == null) {
            return 0;
        }
        return mapTemplate.redPerPlayer() + (extraReds != null ? extraReds : 0);
    }

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Mantis Tiles";
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (BlueTileDraftItem tile : blueTiles) {
            choices.add(produceChoice(tile));
        }
        for (RedTileDraftItem tile : redTiles) {
            choices.add(produceChoice(tile));
        }
        return choices;
    }

    private DraftChoice produceChoice(DraftItem tile) {
        String tileID = tile.ItemId;
        String choiceKey = MantisTileDraftable.makeChoiceKey(tileID);
        String representation = Mapper.getTileRepresentations().get(tileID);
        if (representation == null) {
            representation = tileID;
        }
        TI4Emoji emoji = TileEmojis.getTileEmojiFromTileID(tileID);
        Button button =
                Button.secondary(makeButtonId(choiceKey), representation);
        if(emoji != null) {
            button = button.withEmoji(emoji.asEmoji());
        }
        return new DraftChoice(TYPE, choiceKey, button, tile.getLongDescription(), representation, emoji != null ? emoji.emojiString() : null);
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {

        if (commandKey == null) {
            return "Error: Command key is missing.";
        }

        if (commandKey.startsWith(MantisMapBuildService.ACTION_PREFIX)) {
            MantisMapBuildContext mapBuildContext =
                    MantisMapBuildContext.from(draftManager, this);
            return MantisMapBuildService.handleAction(event, mapBuildContext, commandKey);
        }

        return "Unknown command: " + commandKey;
    }

    @Override
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        String superError = super.isValidDraftChoice(draftManager, playerUserId, choice);
        if (superError != null) {
            return superError;
        }

        Game game = draftManager.getGame();
        MapTemplateModel mapTemplate =
                game.getMapTemplateID() != null ? Mapper.getMapTemplate(game.getMapTemplateID()) : null;
        if (mapTemplate == null) {
            return "Error: Could not find map template for game.";
        }

        List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, TYPE);
        int bpp = getTotalBluePerPlayer(mapTemplate);
        int rpp = getTotalRedPerPlayer(mapTemplate);
        int bluePicked = 0;
        int redPicked = 0;
        for (DraftChoice pick : playerPicks) {
            Category category = getItemCategory(pick.getChoiceKey());
            if (category == Category.BLUETILE) {
                bluePicked++;
            } else if (category == Category.REDTILE) {
                redPicked++;
            }
        }

        Category choiceCategory = getItemCategory(choice.getChoiceKey());
        if (choiceCategory == null) {
            return "Error: Could not find category for choice: " + choice.getUnformattedName() + " ("
                    + choice.getChoiceKey() + ").";
        }
        if (choiceCategory == Category.BLUETILE) {
            if (bluePicked >= bpp) {
                return DraftButtonService.USER_MISTAKE_PREFIX
                        + "You have already picked the maximum number of blue tiles (" + bpp + ").";
            }
        } else if (choiceCategory == Category.REDTILE) {
            if (redPicked >= rpp) {
                return DraftButtonService.USER_MISTAKE_PREFIX
                        + "You have already picked the maximum number of red tiles (" + rpp + ").";
            }
        } else {
            return "Error: Choice " + choice.getUnformattedName() + " (" + choice.getChoiceKey()
                    + ") is not a blue or red tile.";
        }

        return null;
    }

    @Override
    public void postApplyDraftPick(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {

        // TODO: Send rendering of all tile picks to private info thread, as a summary
        // for the player.

        Player player = draftManager.getGame().getPlayer(playerUserId);
        if (player == null) {
            BotLogger.warning(
                    "MantisTileDraftable.postApplyDraftPick: Could not find player with user ID " + playerUserId);
            return;
        }

        StringBuilder summary = new StringBuilder("You picked the tiles: ");
        List<DraftChoice> picks =
                draftManager.getPlayerStates().get(playerUserId).getPicks(TYPE);
        if (picks == null || picks.isEmpty()) {
            summary.append("None");
        } else {
            List<String> pickNames = new ArrayList<>();
            for (DraftChoice pick : picks) {
                pickNames.add(pick.getFormattedName());
            }
            summary.append(String.join(", ", pickNames));
        }

        ThreadChannel cardsInfoChannel = player.getCardsInfoThread();
        if (cardsInfoChannel == null) {
            return; // Not sure this needs to be logged anywhere
        }

        cardsInfoChannel.getHistory().retrievePast(10).queue(messages -> {
            messages.stream()
                    .filter(msg -> !msg.isUsingComponentsV2())
                    .filter(msg -> msg.getContentRaw().startsWith("You picked the tiles: "))
                    .findFirst()
                    .ifPresentOrElse(
                            msg -> msg.editMessage(summary.toString()).queue(), () -> cardsInfoChannel
                                    .sendMessage(summary.toString())
                                    .queue());
        });
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                TYPE, null, null, "No tile picked", "No tile picked", TileEmojis.TileBlackBack.emojiString());
    }

    @Override
    public String save() {
        List<String> allTileIDs = new ArrayList<>();
        for (BlueTileDraftItem tile : blueTiles) {
            allTileIDs.add("b" + tile.ItemId);
        }
        for (RedTileDraftItem tile : redTiles) {
            allTileIDs.add("r" + tile.ItemId);
        }
        for (String tileID : discardedTileIDs) {
            allTileIDs.add("d" + tileID);
        }
        for (String tileId : mulliganTileIDs) {
            allTileIDs.add("m" + tileId);
        }
        if (drawnTileId != null && !drawnTileId.isEmpty()) {
            allTileIDs.add("x" + drawnTileId);
        }
        if (extraBlues != null) {
            allTileIDs.add("y" + extraBlues);
        }
        if (extraReds != null) {
            allTileIDs.add("z" + extraReds);
        }
        if (mulligans != null) {
            allTileIDs.add("w" + mulligans);
        }

        return String.join(Draftable.SAVE_SEPARATOR, allTileIDs);
    }

    @Override
    public void load(String data) {
        blueTiles.clear();
        redTiles.clear();
        discardedTileIDs.clear();
        mulliganTileIDs.clear();
        if (data == null || data.isEmpty()) {
            return;
        }
        String[] labeledTileIDs = data.split(Draftable.SAVE_SEPARATOR);
        for (String labeledTileId : labeledTileIDs) {
            Character label = labeledTileId.charAt(0);
            String datum = labeledTileId.length() > 1 ? labeledTileId.substring(1) : "";

            // If there is no label, infer the label from the tile back
            if (!Character.isAlphabetic(labeledTileId.charAt(0))) {
                datum = labeledTileId;
                TileModel tileModel = TileHelper.getTileById(datum);
                if (tileModel == null || tileModel.getTileBack() == null) {
                    BotLogger.warning(
                            "MantisTileDraftable.load: Could not find tile with ID '" + datum + "' in data: " + data);
                    continue;
                }
                if (tileModel.getTileBack() == TileBack.BLUE) {
                    label = 'b';
                } else if (tileModel.getTileBack() == TileBack.RED) {
                    label = 'r';
                }
            }
            if (label.equals('b')) {
                blueTiles.add((BlueTileDraftItem) DraftItem.generate(Category.BLUETILE, datum));
            } else if (label.equals('r')) {
                redTiles.add((RedTileDraftItem) DraftItem.generate(Category.REDTILE, datum));
            } else if (label.equals('d')) {
                discardedTileIDs.add(datum);
            } else if (label.equals('m')) {
                mulliganTileIDs.add(datum);
            } else if (label.equals('x')) {
                drawnTileId = datum;
            } else if (label.equals('y')) {
                extraBlues = Integer.parseInt(datum);
            } else if (label.equals('z')) {
                extraReds = Integer.parseInt(datum);
            } else if (label.equals('w')) {
                mulligans = Integer.parseInt(datum);
            } else {
                BotLogger.warning("MantisTileDraftable.load: Unknown tile label '" + label + "' in data: " + data);
            }
        }
    }

    @Override
    public String validateState(DraftManager draftManager) {
        Set<String> tileIDs = new HashSet<>();
        for (BlueTileDraftItem tile : blueTiles) {
            if (tile == null || tile.ItemId == null || tile.ItemId.isEmpty()) {
                return "One of the blue tiles is missing a tile ID.";
            }
            if (TileHelper.getTileById(tile.ItemId) == null) {
                return "Blue tile ID " + tile.ItemId + " is not known as a tile.";
            }
            if (TileHelper.getTileById(tile.ItemId).getTileBack() != TileBack.BLUE) {
                return "Tile ID " + tile.ItemId + " is not a blue tile.";
            }
            if (tileIDs.contains(tile.ItemId)) {
                return "Tile ID " + tile.ItemId + " is duplicated in blue tiles.";
            }
            tileIDs.add(tile.ItemId);
        }
        for (RedTileDraftItem tile : redTiles) {
            if (tile == null || tile.ItemId == null || tile.ItemId.isEmpty()) {
                return "One of the red tiles is missing a tile ID.";
            }
            if (TileHelper.getTileById(tile.ItemId) == null) {
                return "Red tile ID " + tile.ItemId + " is not known as a tile.";
            }
            if (TileHelper.getTileById(tile.ItemId).getTileBack() != TileBack.RED) {
                return "Tile ID " + tile.ItemId + " is not a red tile.";
            }
            if (tileIDs.contains(tile.ItemId)) {
                return "Tile ID " + tile.ItemId + " is duplicated in red tiles.";
            }
            tileIDs.add(tile.ItemId);
        }
        for (String tileID : discardedTileIDs) {
            if (tileID == null || tileID.isEmpty()) {
                return "One of the discarded tiles is missing a tile ID.";
            }
            if (!tileIDs.contains(tileID)) {
                return "Discarded tile ID " + tileID + " is not found in blue or red tiles.";
            }
        }
        for (String tileID : mulliganTileIDs) {
            if (tileID == null || tileID.isEmpty()) {
                return "One of the mulligan tiles is missing a tile ID.";
            }
            if (!tileIDs.contains(tileID)) {
                return "Mulligan tile ID " + tileID + " is not found in blue or red tiles.";
            }
        }

        String mapTemplateID = draftManager.getGame().getMapTemplateID();
        if (mapTemplateID == null) {
            return "Map template ID is missing. Try `/map set_map_template`";
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateID);
        if (mapTemplate == null) {
            return "Map template ID " + mapTemplateID + " is not valid.  Try `/map set_map_template`";
        }
        int bpp = getTotalBluePerPlayer(mapTemplate);
        int rpp = getTotalRedPerPlayer(mapTemplate);

        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            Player player = draftManager.getGame().getPlayer(playerUserId);
            if (player == null) {
                return "Could not find player with user ID " + playerUserId + ".";
            }
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> mantisPicks = pState.getPicks(TYPE);
            if (mantisPicks == null) {
                continue; // Player hasn't picked anything yet
            }

            int bluePicked = 0;
            int redPicked = 0;
            for (DraftChoice choice : mantisPicks) {
                Category category = getItemCategory(choice.getChoiceKey());
                if (category == Category.BLUETILE) {
                    bluePicked++;
                } else if (category == Category.REDTILE) {
                    redPicked++;
                } else {
                    return "Player " + player.getRepresentation() + " has an invalid tile choice: "
                            + choice.getUnformattedName() + " (" + choice.getChoiceKey() + ").";
                }
            }
            if (bluePicked > bpp) {
                return "Player " + player.getRepresentation() + " has picked too many blue tiles (" + bluePicked
                        + " picked, max is " + bpp + ").";
            }
            if (redPicked > rpp) {
                return "Player " + player.getRepresentation() + " has picked too many red tiles (" + redPicked
                        + " picked, max is " + rpp + ").";
            }
        }

        return null;
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        if (menu == null || !(menu instanceof DraftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
        DraftSystemSettings draftSystemSettings = (DraftSystemSettings) menu;
        Game game = draftSystemSettings.getGame();
        if (game == null) {
            return "Error: Could not find game instance.";
        }

        SourceSettings sourceSettings = draftSystemSettings.getSourceSettings();
        if (sourceSettings == null) {
            return "Error: Could not find source settings.";
        }

        MantisTileDraftableSettings mantisSettings = draftSystemSettings.getMantisTileSettings();
        if (mantisSettings == null) {
            return "Error: Could not find Mantis tile draftable settings.";
        }

        MapTemplateModel mapTemplate = mantisSettings.getMapTemplate().getValue();
        if (mapTemplate == null) {
            mapTemplate = game.getMapTemplateID() != null ? Mapper.getMapTemplate(game.getMapTemplateID()) : null;
            if (mapTemplate == null) {
                return "Error: Could not find map template for game.";
            }
        }

        game.setMapTemplateID(mapTemplate.getAlias());

        this.extraBlues = mantisSettings.getExtraBlues().getVal();
        this.extraReds = mantisSettings.getExtraReds().getVal();
        this.mulligans = mantisSettings.getMulligans().getVal();

        List<ComponentSource> sources = sourceSettings.getTileSources();
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.clear();
        tileManager.addAllDraftTiles(sources);

        int numPlayers = draftSystemSettings.getPlayerUserIds().size();
        int bpp = getTotalBluePerPlayer(mapTemplate);
        int rpp = getTotalRedPerPlayer(mapTemplate);
        int totalBluesNeeded = bpp * numPlayers;
        int totalRedsNeeded = rpp * numPlayers;

        blueTiles.clear();
        redTiles.clear();

        List<DraftItem> blueTileItems = new ArrayList<>();
        for (MiltyDraftTile tile : tileManager.getBlue()) {
            blueTileItems.add(DraftItem.generate(
                    DraftItem.Category.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(blueTileItems, DraftItem.Category.BLUETILE);
        List<DraftItem> selectedBlueTiles =
                blueTileItems.stream().limit(totalBluesNeeded).toList();
        if (selectedBlueTiles.size() < totalBluesNeeded) {
            return "Error: Could only find " + selectedBlueTiles.size() + " blue tiles to add, needed "
                    + totalBluesNeeded + ".";
        }
        blueTiles.addAll(
                selectedBlueTiles.stream().map(item -> (BlueTileDraftItem) item).toList());

        List<DraftItem> redTileItems = new ArrayList<>();
        for (MiltyDraftTile tile : tileManager.getRed()) {
            redTileItems.add(DraftItem.generate(
                    DraftItem.Category.REDTILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(redTileItems, DraftItem.Category.REDTILE);
        List<DraftItem> selectedRedTiles =
                redTileItems.stream().limit(totalRedsNeeded).toList();
        if (selectedRedTiles.size() < totalRedsNeeded) {
            return "Error: Could only find " + selectedRedTiles.size() + " red tiles to add, needed " + totalRedsNeeded
                    + ".";
        }
        redTiles.addAll(
                selectedRedTiles.stream().map(item -> (RedTileDraftItem) item).toList());

        return null;
    }

    @Override
    public String whatsStoppingDraftEnd(DraftManager draftManager) {
        String mapTemplateID = draftManager.getGame().getMapTemplateID();
        if (mapTemplateID == null) {
            return "Map template ID is missing. Try `/map set_map_template`";
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateID);
        if (mapTemplate == null) {
            return "Map template ID " + mapTemplateID + " is not valid.  Try `/map set_map_template`";
        }
        int bpp = getTotalBluePerPlayer(mapTemplate);
        int rpp = getTotalRedPerPlayer(mapTemplate);

        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            Player player = draftManager.getGame().getPlayer(playerUserId);
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> mantisPicks = pState.getPicks(TYPE);
            if (mantisPicks == null) {
                return "Player " + player.getRepresentation() + " has not picked any tiles.";
            }
            int bluePicked = 0;
            int redPicked = 0;
            for (DraftChoice choice : mantisPicks) {
                Category category = getItemCategory(choice.getChoiceKey());
                if (category == Category.BLUETILE) {
                    bluePicked++;
                } else if (category == Category.REDTILE) {
                    redPicked++;
                } else {
                    return "Player " + player.getRepresentation() + " has an invalid tile choice: "
                            + choice.getUnformattedName() + " (" + choice.getChoiceKey() + ").";
                }
            }
            if (bluePicked < bpp) {
                return "Player " + player.getRepresentation() + " needs to pick more blue tiles (" + bluePicked
                        + " picked, out of " + bpp + ").";
            }
            if (redPicked < rpp) {
                return "Player " + player.getRepresentation() + " needs to pick more red tiles (" + redPicked
                        + " picked, out of " + rpp + ").";
            }
        }

        return null;
    }

    @Override
    public void onDraftEnd(DraftManager draftManager) {
        // Start the map building process
        discardedTileIDs.clear();
        mulliganTileIDs.clear();
        drawnTileId = null;
        MantisMapBuildContext mapBuildContext =
                MantisMapBuildContext.from(draftManager, this);
        MantisMapBuildService.initializeMapBuilding(mapBuildContext);
    }

    @Override
    public String whatsStoppingSetup(DraftManager draftManager) {

        Game game = draftManager.getGame();
        String mapTemplateID = game.getMapTemplateID();
        MapTemplateModel mapTemplate = mapTemplateID != null ? Mapper.getMapTemplate(mapTemplateID) : null;
        if (mapTemplate == null) {
            return "Map template ID is missing or invalid. Try `/map set_map_template`";
        }

        for (MapTemplateTile templateTile : mapTemplate.getTemplateTiles()) {
            if (templateTile.getMiltyTileIndex() == null) continue;
            if (templateTile.getPlayerNumber() == null) continue;
            if (templateTile.getHome() != null && templateTile.getHome()) {
                continue; // Home system, no tile needed
            }

            // If a template position has a tile index and a player number,
            // ensure that the game has an actual tile placed there.
            String tilePosition = templateTile.getPos();
            Tile tile = game.getTileMap().get(tilePosition);
            if (tile == null || TileHelper.isDraftTile(tile.getTileModel())) {
                return "Map template position " + tilePosition
                        + " needs a tile placed on it. If the map build gets stuck, try `/draft mantis_tile start_building` OR `/map add_tile` to do it manually.";
            }
        }

        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {

        // Do nothing, this is a map generation item
        return null;
    }
}
