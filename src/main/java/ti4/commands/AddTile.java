package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.ResourceHelper;
import ti4.generator.GenerateMap;
import ti4.generator.PositionMapper;
import ti4.generator.TilesMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;

public class AddTile implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.ADD_TILE);
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
            MessageHelper.replyToMessage(event, "Set your active map using: :set_map mapname");
        } else {


            String planetTileName = AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());
            String position = event.getOptions().get(1).getAsString();
            if (!PositionMapper.isPositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return;
            }

            String tileName = TilesMapper.getTileName(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return;
            }

            Tile tile = new Tile(planetTileName, position);
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            userActiveMap.setTile(tile);

            MapSaveLoadManager.saveMap(userActiveMap);

            File file = GenerateMap.getInstance().saveImage(userActiveMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.ADD_TILE, "Add tile to map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map")
                                .setRequired(true))

        );
    }
}
