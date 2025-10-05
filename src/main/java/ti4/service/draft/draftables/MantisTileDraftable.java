package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;
import ti4.model.DraftErrataModel;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel.TileBack;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TileEmojis;
import ti4.service.milty.MiltyDraftTile;

public class MantisTileDraftable extends Draftable {

    // Used in drafting
    private final List<BlueTileDraftItem> blueTiles = new ArrayList<>();
    private final List<RedTileDraftItem> redTiles = new ArrayList<>();

    // Used in map building
    @Getter
    private final List<String> discardedTileIDs = new ArrayList<>();
    @Getter
    private final List<String> mulliganTileIDs = new ArrayList<>();

    public static final DraftableType TYPE = DraftableType.of("MantisTile");

    public Category getItemCategory(String choiceKey) {
        if (choiceKey == null) return null;
        String tileID = getItemId(choiceKey);
        if(tileID == null) return null;
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

    public String getItemId(String choiceKey) {
        if (choiceKey == null) return null;
        if (!choiceKey.startsWith("tile")) return null;
        return choiceKey.substring("tile".length());
    }

    public DraftItem getDraftItem(String choiceKey) {
        String tileID = getItemId(choiceKey);
        if(tileID == null) return null;
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

    @Override
    public DraftableType getType() {
        return TYPE;
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
        String choiceKey = "tile" + tileID;
        String representation = Mapper.getTileRepresentations().get(tileID);
        if(representation == null) {
            representation = tileID;
        }
        TI4Emoji emoji = TileEmojis.getTileEmojiFromTileID(tileID);
        Button button = Button.secondary(makeButtonId(choiceKey), representation).withEmoji(emoji.asEmoji());
        return new DraftChoice(TYPE, choiceKey, button, representation, representation, emoji.emojiString());
    }

	@Override
	public String handleCustomCommand(GenericInteractionCreateEvent event, DraftManager draftManager,
			String playerUserId, String commandKey) {
		
        // TODO: Will need to handle map building...
        return null;
	}

	@Override
	public DraftChoice getNothingPickedChoice() {
		return new DraftChoice(TYPE, null, null, "No tile picked", "No tile picked", TileEmojis.TileBlackBack.emojiString());
	}

	@Override
	public String save() {
        List<String> allTileIDs = new ArrayList<>();
        for (BlueTileDraftItem tile : blueTiles) {
            allTileIDs.add(tile.ItemId);
        }
        for (RedTileDraftItem tile : redTiles) {
            allTileIDs.add(tile.ItemId);
        }
        allTileIDs.add("DISCARDS");
        allTileIDs.addAll(discardedTileIDs);
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
        String[] tileIDs = data.split(",");
        boolean discards = false;
        boolean mulligans = false;
        for (String tileID : tileIDs) {
            if(tileID.equals("DISCARDS")) {
                discards = true;
                continue;
            }
            if(tileID.equals("MULLIGANS")) {
                mulligans = true;
                continue;
            }
            if(mulligans) {
                mulliganTileIDs.add(tileID);
                continue;
            }
            if(discards) {
                discardedTileIDs.add(tileID);
                continue;
            }

            MiltyDraftTile tile = DraftTileManager.findTile(tileID);
            if (tile != null) {
                if (tile.getTile().getTileModel().getTileBack().equals(TileBack.BLUE)) {
                    blueTiles.add((BlueTileDraftItem) DraftItem.generate(Category.BLUETILE, tileID));
                } else if (tile.getTile().getTileModel().getTileBack().equals(TileBack.RED)) {
                    redTiles.add((RedTileDraftItem) DraftItem.generate(Category.REDTILE, tileID));
                }
                else {
                    BotLogger.warning("MantisTileDraftable.load: Tile " + tileID + " is not blue or red, shouldn't be in save data.");
                }
            }
        }
	}

	@Override
	public String validateState(DraftManager draftManager) {
        String mapTemplateID = draftManager.getGame().getMapTemplateID();
        if (mapTemplateID == null) {
            return "Map template ID is missing. Try `/map set_map_template`";
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateID);
        if (mapTemplate == null) {
            return "Map template ID " + mapTemplateID + " is not valid.  Try `/map set_map_template`";
        }
        int bpp = mapTemplate.bluePerPlayer();
        int rpp = mapTemplate.redPerPlayer();

        for(Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            Player player = draftManager.getGame().getPlayer(playerUserId);
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> mantisPicks = pState.getPicks(TYPE);
            int bluePicked = 0;
            int redPicked = 0;
            for(DraftChoice choice : mantisPicks) {
                Category category = getItemCategory(choice.getChoiceKey());
                if(category == Category.BLUETILE) {
                    bluePicked++;
                }
                else if(category == Category.REDTILE) {
                    redPicked++;
                }
                else {
                    return "Player " + player.getRepresentation() + " has an invalid tile choice: " + choice.getUnformattedName() + " (" + choice.getChoiceKey() + ").";
                }
            }
            if(bluePicked > bpp) {
                return "Player " + player.getRepresentation() + " has picked too many blue tiles (" + bluePicked + " picked, max is " + bpp + ").";
            }
            if(redPicked > rpp) {
                return "Player " + player.getRepresentation() + " has picked too many red tiles (" + redPicked + " picked, max is " + rpp + ").";
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
        
        MapTemplateModel mapTemplate =
                draftSystemSettings.getSliceSettings().getMapTemplate().getValue();
        if (mapTemplate == null) {
            mapTemplate = game.getMapTemplateID() != null ? Mapper.getMapTemplate(game.getMapTemplateID()) : null;
            if (mapTemplate == null) {
                return "Error: Could not find map template for game.";
            }
        }

        SourceSettings sourceSettings = draftSystemSettings.getSourceSettings();
        if (sourceSettings == null) {
            return "Error: Could not find source settings.";
        }

        List<ComponentSource> sources = sourceSettings.getTileSources();
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.clear();
        tileManager.addAllDraftTiles(sources);

        int numPlayers = draftSystemSettings.getPlayerUserIds().size();
        int bpp = mapTemplate.bluePerPlayer();
        int rpp = mapTemplate.redPerPlayer();
        int totalBluesNeeded = bpp * numPlayers;
        int totalRedsNeeded = rpp * numPlayers;

        blueTiles.clear();
        redTiles.clear();

        List<DraftItem> blueTileItems = new ArrayList<>();
        for (MiltyDraftTile tile : tileManager.getBlue()) {
            blueTileItems.add(DraftItem.generate(DraftItem.Category.BLUETILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(blueTileItems, DraftItem.Category.BLUETILE);
        List<DraftItem> selectedBlueTiles = blueTileItems.stream().limit(totalBluesNeeded).toList();
        if(selectedBlueTiles.size() < totalBluesNeeded) {
            return "Error: Could only find " + selectedBlueTiles.size() + " blue tiles to add, needed " + totalBluesNeeded + ".";
        }
        blueTiles.addAll(selectedBlueTiles.stream().map(item -> (BlueTileDraftItem)item).toList());

        List<DraftItem> redTileItems = new ArrayList<>();
        for (MiltyDraftTile tile : tileManager.getRed()) {
            redTileItems.add(DraftItem.generate(DraftItem.Category.REDTILE, tile.getTile().getTileID()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(redTileItems, DraftItem.Category.REDTILE);
        List<DraftItem> selectedRedTiles = redTileItems.stream().limit(totalRedsNeeded).toList();
        if(selectedRedTiles.size() < totalRedsNeeded) {
            return "Error: Could only find " + selectedRedTiles.size() + " red tiles to add, needed " + totalRedsNeeded + ".";
        }
        redTiles.addAll(selectedRedTiles.stream().map(item -> (RedTileDraftItem)item).toList());

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
        int bpp = mapTemplate.bluePerPlayer();
        int rpp = mapTemplate.redPerPlayer();

        for(Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            Player player = draftManager.getGame().getPlayer(playerUserId);
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> mantisPicks = pState.getPicks(TYPE);
            int bluePicked = 0;
            int redPicked = 0;
            for(DraftChoice choice : mantisPicks) {
                Category category = getItemCategory(choice.getChoiceKey());
                if(category == Category.BLUETILE) {
                    bluePicked++;
                }
                else if(category == Category.REDTILE) {
                    redPicked++;
                }
                else {
                    return "Player " + player.getRepresentation() + " has an invalid tile choice: " + choice.getUnformattedName() + " (" + choice.getChoiceKey() + ").";
                }
            }
            if(bluePicked < bpp) {
                return "Player " + player.getRepresentation() + " needs to pick more blue tiles (" + bluePicked + " picked, out of " + bpp + ").";
            }
            if(redPicked < rpp) {
                return "Player " + player.getRepresentation() + " needs to pick more red tiles (" + redPicked + " picked, out of " + rpp + ").";
            }
        }

        return null;
	}

	@Override
	public Consumer<Player> setupPlayer(DraftManager draftManager, String playerUserId,
			PlayerSetupState playerSetupState) {
        
        // Do nothing, this is a map generation item
        return null;
	}

    @Override
    public void onDraftEnd(DraftManager draftManager) {
        // Start the map building process
        discardedTileIDs.clear();
        MantisMapBuildService.initializeMapBuilding(draftManager);
    }
}
