package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.ResourceHelper;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;

public class AddTileList implements Command {

    @Override
    public String getActionID() {
        return Constants.ADD_TILE_LIST;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
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
        } else {


            String tileList = event.getOptions().get(0).getAsString().toLowerCase();
            HashMap<String, String> mappedTilesToPosition = MapStringMapper.getMappedTilesToPosition(tileList, userActiveMap);
            if (mappedTilesToPosition.isEmpty()) {
                MessageHelper.replyToMessage(event, "Could not map all tiles to map positions");
                return;
            }

            userActiveMap.clearTileMap();
            for (java.util.Map.Entry<String, String> entry : mappedTilesToPosition.entrySet()) {
                String tileID = entry.getValue();
                if (tileID.equals("0")) {
                    continue;
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

            MapSaveLoadManager.saveMap(userActiveMap, event);

            File file = GenerateMap.getInstance().saveImage(userActiveMap, event);
            MessageHelper.replyToMessage(event, file);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Add tile list to generate map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_LIST, "Tile list")
                                .setRequired(true))

        );
    }
}
