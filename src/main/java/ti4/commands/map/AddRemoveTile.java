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
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;

abstract public class AddRemoveTile implements Command {
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
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Map userActiveMap = tileParsing(event, userID, mapManager);
            if (userActiveMap == null) return;
            MapSaveLoadManager.saveMap(userActiveMap);
            File file = GenerateMap.getInstance().saveImage(userActiveMap, event);
            MessageHelper.replyToMessage(event, file);
        }
    }

    protected Map tileParsing(SlashCommandInteractionEvent event, String userID, MapManager mapManager) {
        String planetTileName = AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());
        String position = event.getOptions().get(1).getAsString();
        if (!PositionMapper.isTilePositionValid(position, mapManager.getUserActiveMap(userID))) {
            MessageHelper.replyToMessage(event, "Position tile not allowed");
            return null;
        }

        String tileName = Mapper.getTileID(planetTileName);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return null;
        }

        Tile tile = new Tile(planetTileName, position);
        if (planetTileName.equals("18")){
            tile.addToken("token_custodian.png", "mr");
        }
        Map userActiveMap = mapManager.getUserActiveMap(userID);
        tileAction(tile, position, userActiveMap);
        return userActiveMap;
    }

    abstract protected void tileAction(Tile tile, String position, Map userActiveMap);

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map")
                                .setRequired(true))

        );
    }

    abstract protected String getActionDescription();
}
