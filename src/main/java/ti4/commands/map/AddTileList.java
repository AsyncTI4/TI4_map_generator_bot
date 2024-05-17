package ti4.commands.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.ResourceHelper;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.MapStringMapper;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class AddTileList extends MapSubcommandData {
    public AddTileList() {
        super(Constants.ADD_TILE_LIST, "Add tile list to generate map");
        addOption(OptionType.STRING, Constants.TILE_LIST, "Tile list in TTPG/TTS format", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }

        String userID = member.getId();
        GameManager gameManager = GameManager.getInstance();
        Game game = gameManager.getUserActiveGame(userID);
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }

        String tileList = event.getOption(Constants.TILE_LIST, "", OptionMapping::getAsString);
        tileList = tileList.replaceAll(",", " ");
        tileList = tileList.replaceAll("  ", " ");

        addTileListToMap(game, tileList, event);

        GameSaveLoadManager.saveMap(game, event);

        MapGenerator.saveImage(game, event).thenAccept(fileUpload -> MessageHelper.replyToMessage(event, fileUpload));
    }

    public static void addTileListToMap(Game game, String tileList, GenericInteractionCreateEvent event) {
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

        if (!badTiles.isEmpty()) MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There were some bad tiles that were replaced with red tiles: " + badTiles + "\n");

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
            if (!TileHelper.getAllTiles().containsKey(tileID)) {
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
            AddTile.addCustodianToken(tile);
            game.setTile(tile);
        }
        return badTiles;
    }

    public static void finishSetup(Game game, GenericInteractionCreateEvent event) {
        try {
            Tile tile;
            tile = new Tile(AliasHandler.resolveTile(Constants.MALLICE), "TL");
            game.setTile(tile);
            if (game.getTileByPosition("000") == null) {
                tile = new Tile(AliasHandler.resolveTile(Constants.MR), "000");
                AddTile.addCustodianToken(tile);
                game.setTile(tile);
            }
        } catch (Exception e) {
            BotLogger.log("Could not add setup and Mallice tiles", e);
        }

        if (!game.isBaseGameMode()) {
            MessageChannel channel = event != null ? event.getMessageChannel() : game.getMainGameChannel();
            AddFrontierTokens.parsingForTile(event, game);
            MessageHelper.sendMessageToChannel(channel, Emojis.Frontier + "Frontier Tokens have been added to empty spaces.");
        }
    }
}
