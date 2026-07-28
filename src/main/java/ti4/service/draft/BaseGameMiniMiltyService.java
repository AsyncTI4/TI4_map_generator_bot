package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.commands.game.WeirdGameSetup;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.settingsFramework.menus.BaseGameMiniMiltySettings;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class BaseGameMiniMiltyService {
    private static final int BLUE_TILES_PER_SLICE = 3;
    private static final int RED_TILES_PER_SLICE = 2;

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

        Set<String> bannedFactions =
                new HashSet<>(settings.getFactionSettings().getBanFactions().getKeys());
        List<String> prioritizedFactions = settings.getFactionSettings().getPriFactions().getKeys().stream()
                .filter(faction -> !bannedFactions.contains(faction))
                .toList();

        FactionDraftable factionDraftable = new FactionDraftable();
        factionDraftable.initialize(
                settings.getFactionSettings().getNumFactions().getVal(),
                settings.getFactionSources(),
                prioritizedFactions,
                new ArrayList<>(bannedFactions));
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

        PartialMapService.placeFromTemplate(template, game);
        return applyRandomBaseGameTiles(game, template);
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
