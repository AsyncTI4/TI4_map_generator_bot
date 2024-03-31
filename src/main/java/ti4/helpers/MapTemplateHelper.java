package ti4.helpers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ti4.commands.map.AddTileList;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftManager.PlayerDraft;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;

public class MapTemplateHelper {

    public static void buildMapFromMiltyData(Game game, String mapTemplate) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        MapTemplateModel template = Mapper.getMapTemplate(mapTemplate);
        List<PlayerDraft> speakerOrdered = manager.getDraft().values().stream()
            .sorted(Comparator.comparing(PlayerDraft::getOrder))
            .toList();
        
        Map<String, String> positionMap = new HashMap<>();
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            Entry<String, String> tileEntry = inferTileFromTemplateAndDraft(templateTile, speakerOrdered);
            positionMap.put(tileEntry.getKey(), tileEntry.getValue());
        }
        
        List<String> badTiles = AddTileList.addTileMapToGame(game, positionMap);
        if (!badTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "There were some bad tiles that were replaced with red tiles: " + badTiles + "\n");
            throw new Exception("Bad tiles, aborting setup: " + game.getName());
        }
        AddTileList.finishSetup(game, null);
    }

    private static Entry<String, String> inferTileFromTemplateAndDraft(MapTemplateTile templateTile, List<PlayerDraft> draft) throws Exception {
        String tileId = null;
        String position = templateTile.getPos();
        if (templateTile.getStaticTileId() != null) {
            tileId = templateTile.getStaticTileId();
        } else if (templateTile.getPlayerNumber() != null) {
            PlayerDraft player = draft.stream()
                .filter(p -> p.getOrder() == templateTile.getPlayerNumber())
                .findFirst().orElse(null);
            if (player == null) {
                throw new Exception("Something went wrong, could not find player at speaker order: " + templateTile.getPlayerNumber());
            }
            if (templateTile.getMiltyTileIndex() != null) {
                int index = templateTile.getMiltyTileIndex();
                tileId = player.getSlice().getTiles().get(index).getTile().getTileID();
            } else if (templateTile.getHome() != null && templateTile.getHome()) {
                FactionModel faction = Mapper.getFaction(player.getFaction());
                tileId = faction.getHomeSystem();
            }
        }

        if (position == null || tileId == null) {
            String tileStr = templateTile.toString();
            throw new Exception("Unable to map template tile to draft tile. Template file may be improperly formatted: " + tileStr);
        }
        return Map.entry(position, AliasHandler.resolveTile(tileId));
    }
}
