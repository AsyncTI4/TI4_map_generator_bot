package ti4.service.map;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Emojis;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.MapStringMapper;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.explore.AddFrontierTokensService;

@UtilityClass
public class AddTileListService {

    public static void addTileListToMap(Game game, String tileList, GenericInteractionCreateEvent event) {
        tileList = tileList.replace(",", " ");
        tileList = tileList.replace("  ", " ");

        Map<String, String> mappedTilesToPosition = MapStringMapper.getMappedTilesToPosition(tileList, game);
        if (mappedTilesToPosition.isEmpty()) {
            MessageHelper.replyToMessage(event, "Could not map all tiles to map positions");
            return;
        }

        List<String> badTiles = new ArrayList<>();
        game.clearTileMap();
        try {
            badTiles = addTileMapToGame(game, mappedTilesToPosition);
        } catch (Exception e) {
            BotLogger.log(e.getMessage(), e);
            MessageHelper.replyToMessage(event, e.getMessage());
        }

        MessageHelper.sendMessageToEventChannel(event, "Setting Map String to: ```\n" + tileList + "\n```");
        ShowGameService.simpleShowGame(game, event, DisplayType.map);

        if (!badTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There were some bad tiles that were replaced with red tiles: " + badTiles + "\n");
        }

        finishSetup(game, event);
    }

    public static List<String> addTileMapToGame(Game game, Map<String, String> tileMap) throws Exception {
        List<String> badTiles = new ArrayList<>();
        game.clearTileMap();
        for (Map.Entry<String, String> entry : tileMap.entrySet()) {
            String tileID = entry.getValue().toLowerCase();
            if ("-1".equals(tileID)) {
                continue;
            }
            if ("0".equals(tileID)) {
                tileID = "0g";
            }
            if (!TileHelper.isValidTile(tileID)) {
                badTiles.add(tileID);
                tileID = "0gray";
            }
            String tileName = Mapper.getTileID(tileID);
            String position = entry.getKey();
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                throw new Exception("Could not find tile: " + tileID);
            }
            Tile tile = new Tile(tileID, position);
            AddTileService.addCustodianToken(tile);
            game.setTile(tile);
        }
        return badTiles;
    }

    public static void finishSetup(Game game, @Nullable GenericInteractionCreateEvent event) {
        try {
            Tile tile;
            tile = new Tile(AliasHandler.resolveTile(Constants.MALLICE), "TL");
            game.setTile(tile);
            if (game.getTileByPosition("000") == null) {
                tile = new Tile(AliasHandler.resolveTile(Constants.MR), "000");
                AddTileService.addCustodianToken(tile);
                game.setTile(tile);
            }
        } catch (Exception e) {
            BotLogger.log("Could not add setup and Mallice tiles", e);
        }

        MessageChannel channel = event != null ? event.getMessageChannel() : game.getMainGameChannel();
        if (!game.isBaseGameMode()) {
            AddFrontierTokensService.addFrontierTokens(event, game);
            MessageHelper.sendMessageToChannel(channel, Emojis.Frontier + "Frontier Tokens have been added to empty spaces.");
        }
        
        MessageHelper.sendMessageToChannelWithButtons(
            game.getMainGameChannel(), "Press this button after every player is setup",
            List.of(Buttons.green("deal2SOToAll", "Deal 2 SO To All")));

        if (game.getRealPlayers().size() < game.getPlayers().size()) {
            ButtonHelper.offerPlayerSetupButtons(channel, game);
        }
    }
}
