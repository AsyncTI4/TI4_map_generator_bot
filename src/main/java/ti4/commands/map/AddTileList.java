package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.ResourceHelper;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.*;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        MapManager mapManager = MapManager.getInstance();
        Map userActiveMap = mapManager.getUserActiveMap(userID);
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }

        String tileList = event.getOption(Constants.TILE_LIST, "", OptionMapping::getAsString);
        tileList = tileList.replaceAll(",", "");
        HashMap<String, String> mappedTilesToPosition = MapStringMapper.getMappedTilesToPosition(tileList, userActiveMap);
        if (mappedTilesToPosition.isEmpty()) {
            MessageHelper.replyToMessage(event, "Could not map all tiles to map positions");
            return;
        }

        List<String> badTiles = new ArrayList<>();
        userActiveMap.clearTileMap();
        for (java.util.Map.Entry<String, String> entry : mappedTilesToPosition.entrySet()) {
            String tileID = entry.getValue().toLowerCase();
            if (tileID.equals("-1")) {
                continue;
            }
            if (tileID.equals("0")) {
                tileID = "0g";
            }
            if (!TileHelper.getAllTiles().containsKey(tileID)) {
                badTiles.add(tileID);
                tileID = "0r";
            }
            String tileName = Mapper.getTileID(tileID);
            String position = entry.getKey();
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + tileID);
                return;
            }
            Tile tile = new Tile(tileID, position);
            AddTile.addCustodianToken(tile);
            userActiveMap.setTile(tile);
        }

        if (!badTiles.isEmpty()) MessageHelper.sendMessageToChannel(event.getChannel(), "There were some bad tiles that were replaced with red tiles: " + badTiles.toString() + "\n");

        try {
            Tile tile;
            tile = new Tile(AliasHandler.resolveTile(Constants.MALLICE), "TL");
            userActiveMap.setTile(tile);
            if (!tileList.startsWith("{") && !tileList.contains("}")) {
                tile = new Tile(AliasHandler.resolveTile(Constants.MR), "000");
                AddTile.addCustodianToken(tile);
                userActiveMap.setTile(tile);
            }
        } catch (Exception e) {
            BotLogger.log("Could not add setup and Mallice tiles", e);
        }

        new AddFrontierTokens().parsingForTile(event, userActiveMap);

        MapSaveLoadManager.saveMap(userActiveMap, event);

        File file = GenerateMap.getInstance().saveImage(userActiveMap, event);
        MessageHelper.replyToMessage(event, file);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Emojis.Frontier + "Frontier Tokens have been added to empty spaces.");
    }
}
