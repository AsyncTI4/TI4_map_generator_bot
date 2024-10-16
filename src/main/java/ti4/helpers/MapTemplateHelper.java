package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.map.AddTile;
import ti4.commands.map.AddTileList;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftSlice;
import ti4.commands.milty.MiltyDraftManager.PlayerDraft;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;

public class MapTemplateHelper {

    public static void buildMapFromMiltyData(Game game, String mapTemplate) throws Exception {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        MapTemplateModel template = Mapper.getMapTemplate(mapTemplate);
        List<PlayerDraft> speakerOrdered = manager.getDraft().values().stream()
            .sorted(Comparator.comparing(PlayerDraft::getPosition))
            .toList();

        Map<String, String> positionMap = new HashMap<>();
        List<MapTemplateTile> badTemplateTiles = new ArrayList<>();
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            Entry<String, String> tileEntry = inferTileFromTemplateAndDraft(templateTile, speakerOrdered);
            if (tileEntry == null) {
                badTemplateTiles.add(templateTile);
            } else {
                positionMap.put(tileEntry.getKey(), tileEntry.getValue());
            }
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
                .filter(p -> p.getPosition() == templateTile.getPlayerNumber())
                .findFirst().orElse(null);
            if (player == null) {
                throw new Exception("Something went wrong, could not find player at speaker order: " + templateTile.getPlayerNumber());
            }
            if (templateTile.getMiltyTileIndex() != null) {
                int index = templateTile.getMiltyTileIndex();
                tileId = player.getSlice().getTiles().get(index).getTile().getTileID();
            } else if (templateTile.getHome() != null && templateTile.getHome()) {
                FactionModel faction = Mapper.getFaction(player.getFaction());
                if (faction.getAlias().startsWith("keleres")) return null; // don't fill in keleres tile
                tileId = faction.getHomeSystem();
            }
        }

        if (position == null || tileId == null) {
            // don't need to error out here
            return null;
        }
        return Map.entry(position, AliasHandler.resolveTile(tileId));
    }

    public static String getPlayerHomeSystemLocation(PlayerDraft pd, String mapTemplate) {
        MapTemplateModel template = Mapper.getMapTemplate(mapTemplate);
        for (MapTemplateTile t : template.getTemplateTiles()) {
            if (t.getPlayerNumber() != null && t.getPlayerNumber() == pd.getPosition()) {
                if (pd.getFaction() != null && t.getHome() != null && t.getHome()) {
                    return t.getPos();
                }
            }
        }
        return null;
    }

    public static void buildPartialMapFromMiltyData(Game game, GenericInteractionCreateEvent event, String mapTemplate) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        MapTemplateModel template = Mapper.getMapTemplate(mapTemplate);
        List<Player> players = manager.getPlayers().stream().map(p -> game.getPlayer(p)).toList();

        List<String> backupColors = Arrays.asList("red", "blue", "yellow", "emerald", "lavender", "petrol", "chocolate",
            "ethereal", "forest", "gold", "green", "grey", "navy", "spring", "teal", "black", "lightgrey", "rainbow",
            "turquoise", "lightbrown", "orange", "pink", "sunset", "bloodred", "brown", "chrome", "purple", "rose", "white", "tan");
        boolean somethingHappened = false;
        // fill in draft tiles for all players
        for (Player p : players) {
            PlayerDraft draft = manager.getPlayerDraft(p);
            Integer playerNum = draft.getPosition();
            String faction = draft.getFaction();
            MiltyDraftSlice slice = draft.getSlice();
            for (MapTemplateTile tile : template.getTemplateTiles()) {
                Tile gameTile = game.getTileByPosition(tile.getPos());
                if (tile.getPos() != null && tile.getPlayerNumber() != null && tile.getPlayerNumber() == playerNum) {
                    if (gameTile != null && !TileHelper.isDraftTile(gameTile.getTileModel())) continue; //already set

                    if (slice != null && tile.getMiltyTileIndex() != null) {
                        String tileID = slice.getTiles().get(tile.getMiltyTileIndex()).getTile().getTileID();
                        tileID = AliasHandler.resolveTile(tileID);

                        Tile toAdd = new Tile(tileID, tile.getPos());
                        game.setTile(toAdd);
                        somethingHappened = true;
                    } else if (faction != null && !faction.startsWith("keleres") && tile.getHome() != null && tile.getHome()) {
                        String tileID = Mapper.getFaction(faction).getHomeSystem();
                        tileID = AliasHandler.resolveTile(tileID);

                        Tile toAdd = new Tile(tileID, tile.getPos());
                        game.setTile(toAdd);
                        somethingHappened = true;
                    }
                } else if (tile.getPos() != null && gameTile == null) {
                    String tileID = null;
                    if (tile.getStaticTileId() != null)
                        tileID = tile.getStaticTileId();
                    else if (tile.getPlayerNumber() != null) {
                        if (tile.getPlayerNumber() != null) {
                            String color = backupColors.get(tile.getPlayerNumber());
                            if (tile.getMiltyTileIndex() != null) {
                                tileID = color + (tile.getMiltyTileIndex() + 1);
                            } else if (tile.getHome() != null) {
                                tileID = color + "blank";
                            }
                        }
                    }
                    if (tileID != null) {
                        tileID = AliasHandler.resolveTile(tileID);
                        Tile toAdd = new Tile(tileID, tile.getPos());
                        game.setTile(toAdd);
                        somethingHappened = true;
                    }
                }

                if (tile.getPos() != null && tile.getCustodians() != null && tile.getCustodians()) {
                    Tile newgametile = game.getTileByPosition(tile.getPos());
                    if (newgametile != null) AddTile.addCustodianToken(newgametile); //only works on MR for now
                }
            }
        }

        if (somethingHappened) {
            ButtonHelper.updateMap(game, event);
        }
    }
}
