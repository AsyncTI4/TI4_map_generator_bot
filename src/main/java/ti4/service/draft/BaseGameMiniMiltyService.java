package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import ti4.discord.interactions.commands.game.WeirdGameSetup;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.settingsFramework.menus.BaseGameMiniMiltySettings;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.map.AddTileListService;
import ti4.service.map.MapStringMapper;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class BaseGameMiniMiltyService {
    private static final String MAP_STRING_FIELD = "mapString";
    private static final int BLUE_TILES_PER_SLICE = 3;
    private static final int RED_TILES_PER_SLICE = 2;

    public static Modal buildMapStringModal(Game game, String navId, String currentMapString) {
        TextInput.Builder mapString = TextInput.create(MAP_STRING_FIELD, TextInputStyle.PARAGRAPH)
                .setPlaceholder("Paste the map string here.")
                .setRequired(true);
        if (currentMapString != null && !currentMapString.isBlank()) {
            mapString.setValue(currentMapString.substring(0, Math.min(currentMapString.length(), 4000)));
        }
        return Modal.create("jmfA_" + navId + "_customMapString", "Add Map String for " + game.getName())
                .addComponents(Label.of("Enter Map String", mapString.build()))
                .build();
    }

    public static String applyMapStringFromModal(ModalInteractionEvent event, BaseGameMiniMiltySettings settings) {
        ModalMapping mapping = event.getValue(MAP_STRING_FIELD);
        if (mapping == null) {
            return "No map string provided.";
        }
        String mapString = mapping.getAsString().trim();
        if (mapString.isEmpty()) {
            settings.setCustomMapString(null);
            return null;
        }
        if (MapStringMapper.getMappedTilesToPosition(mapString, settings.getGame())
                .isEmpty()) {
            return "Could not map the provided map string to tile positions.";
        }
        settings.setCustomMapString(mapString);
        return null;
    }

    public static String startFromSettings(GenericInteractionCreateEvent event, BaseGameMiniMiltySettings settings) {
        Game game = settings.getGame();
        MapTemplateModel template = settings.getResolvedMapTemplate();
        if (template == null) {
            return "No standard map template is available for this player count.";
        }
        if (settings.getPlayerUserIds().isEmpty()) {
            return "Mini-Milty needs at least 1 player.";
        }
        if (MapTemplateHelper.getPlayerHomeSystemLocation(1, template.getAlias()) == null) {
            return "The selected map template does not define valid home system locations.";
        }
        if (template.bluePerPlayer() != 3 || template.redPerPlayer() != 2) {
            return "Mini-Milty currently requires a map template with exactly 3 blue and 2 red tiles per slice.";
        }

        if (!WeirdGameSetup.applyBaseGameMode(event, game)) {
            return "Could not switch the game into base game mode.";
        }
        game.setMapTemplateID(template.getAlias());

        DraftManager draftManager = game.getDraftManager();
        draftManager.resetForNewDraft();
        draftManager.setPlayers(settings.getPlayerUserIds());

        FactionDraftable factionDraftable = new FactionDraftable();
        factionDraftable.initialize(
                settings.getFactionSettings().getNumFactions().getVal(),
                settings.getFactionSources(),
                new ArrayList<>(settings.getFactionSettings().getPriFactions().getKeys()),
                new ArrayList<>(settings.getFactionSettings().getBanFactions().getKeys()));
        draftManager.addDraftable(factionDraftable);

        SpeakerOrderDraftable speakerOrderDraftable = new SpeakerOrderDraftable();
        speakerOrderDraftable.initialize(settings.getPlayerUserIds().size());
        draftManager.addDraftable(speakerOrderDraftable);

        PublicSnakeDraftOrchestrator orchestrator = new PublicSnakeDraftOrchestrator();
        orchestrator.initialize(draftManager, null);
        draftManager.setOrchestrator(orchestrator);

        game.clearTileMap();
        String mapError = prepareDraftMap(event, game, settings);
        if (mapError != null) {
            return mapError;
        }

        ButtonHelper.updateMap(game, event, "Base Game Mini-Milty map preview");
        game.setPhaseOfGame("miltydraft");
        draftManager.tryStartDraft();
        GameManager.save(game, "Base Game Mini-Milty");
        return null;
    }

    private static String prepareDraftMap(
            GenericInteractionCreateEvent event, Game game, BaseGameMiniMiltySettings settings) {
        MapTemplateModel template = settings.getResolvedMapTemplate();
        if (template == null) {
            return "No standard map template is available for this player count.";
        }

        if (settings.hasCustomMapString()) {
            return applyCustomMapString(game, settings, template);
        }

        PartialMapService.placeFromTemplate(template, game);
        return applyRandomBaseGameTiles(game, template);
    }

    private static String applyCustomMapString(
            Game game, BaseGameMiniMiltySettings settings, MapTemplateModel template) {
        Map<String, String> mappedTiles =
                new HashMap<>(MapStringMapper.getMappedTilesToPosition(settings.getCustomMapString(), game));
        if (mappedTiles.isEmpty()) {
            return "Could not map the stored custom map string to tile positions.";
        }

        for (Integer homePosition : template.getSortedHomeSystemLocations()) {
            mappedTiles.remove(homePosition.toString());
        }

        try {
            AddTileListService.addTileMapToGame(game, mappedTiles);
            PartialMapService.placeFromTemplate(template, game);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Could not apply base game Mini-Milty custom map string", e);
            return "Could not apply the custom map string. Check the map string and try again.";
        }

        if (MapTemplateHelper.getPlayerHomeSystemLocation(1, game.getMapTemplateID()) == null) {
            return "The selected map template does not define valid home system locations.";
        }
        return null;
    }

    private static String applyRandomBaseGameTiles(Game game, MapTemplateModel template) {
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.clear();
        tileManager.addAllDraftTiles(List.of(ComponentSource.base));

        List<MiltyDraftTile> blueTiles = new ArrayList<>(tileManager.getBlue());
        List<MiltyDraftTile> redTiles = new ArrayList<>(tileManager.getRed());
        Collections.shuffle(blueTiles);
        Collections.shuffle(redTiles);

        Map<Integer, List<MiltyDraftTile>> tilesByPlayer = new HashMap<>();
        int bluePointer = 0;
        int redPointer = 0;
        for (Integer playerNumber : getSlicePlayerNumbers(template)) {
            if (bluePointer + BLUE_TILES_PER_SLICE > blueTiles.size()
                    || redPointer + RED_TILES_PER_SLICE > redTiles.size()) {
                return "Could not generate enough base-game tiles for the selected map template.";
            }

            List<MiltyDraftTile> sliceTiles = buildSliceTiles(
                    blueTiles.subList(bluePointer, bluePointer + BLUE_TILES_PER_SLICE),
                    redTiles.subList(redPointer, redPointer + RED_TILES_PER_SLICE));

            tilesByPlayer.put(playerNumber, sliceTiles);
            bluePointer += BLUE_TILES_PER_SLICE;
            redPointer += RED_TILES_PER_SLICE;
        }
        placeSliceTiles(game, template, tilesByPlayer);
        return null;
    }

    private static List<Integer> getSlicePlayerNumbers(MapTemplateModel template) {
        List<Integer> playerNumbers = new ArrayList<>();
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            Integer playerNumber = templateTile.getPlayerNumber();
            if (templateTile.getPos() == null || playerNumber == null || templateTile.getMiltyTileIndex() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(templateTile.getHome())) {
                continue;
            }
            if (!playerNumbers.contains(playerNumber)) {
                playerNumbers.add(playerNumber);
            }
        }
        Collections.sort(playerNumbers);
        return playerNumbers;
    }

    private static List<MiltyDraftTile> buildSliceTiles(List<MiltyDraftTile> blueTiles, List<MiltyDraftTile> redTiles) {
        List<MiltyDraftTile> shuffledBlueTiles = new ArrayList<>(blueTiles);
        List<MiltyDraftTile> shuffledRedTiles = new ArrayList<>(redTiles);
        Collections.shuffle(shuffledBlueTiles);
        Collections.shuffle(shuffledRedTiles);

        List<MiltyDraftTile> sliceTiles = new ArrayList<>(shuffledBlueTiles.size() + shuffledRedTiles.size());
        sliceTiles.addAll(shuffledBlueTiles);
        sliceTiles.addAll(shuffledRedTiles);
        Collections.shuffle(sliceTiles);
        return sliceTiles;
    }

    private static void placeSliceTiles(
            Game game, MapTemplateModel template, Map<Integer, List<MiltyDraftTile>> tilesByPlayer) {
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            if (templateTile.getPos() == null || templateTile.getPlayerNumber() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(templateTile.getHome()) || templateTile.getMiltyTileIndex() == null) {
                continue;
            }

            List<MiltyDraftTile> tiles = tilesByPlayer.get(templateTile.getPlayerNumber());
            if (tiles == null) {
                continue;
            }
            int tileIndex = templateTile.getMiltyTileIndex();
            if (tileIndex < 0 || tileIndex >= tiles.size()) {
                continue;
            }

            String tileId = tiles.get(tileIndex).getTile().getTileID();
            game.setTile(new Tile(tileId, templateTile.getPos()));
        }
    }
}
