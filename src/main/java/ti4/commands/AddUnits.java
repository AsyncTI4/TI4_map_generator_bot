package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.ResourceHelper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class AddUnits implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.ADD_UNITS);
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
            MessageHelper.replyToMessage(event, "Set your active map using: /set_map mapname");
        } else {

            String tileID = AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());

            Map activeMap = mapManager.getUserActiveMap(userID);
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                MessageHelper.replyToMessage(event, "Tile in map not found");
                return;
            }
            String color = event.getOptions().get(1).getAsString().toLowerCase();
            if (!Mapper.isColorValid(color)) {
                MessageHelper.replyToMessage(event, "Color not valid");
                return;
            }

            String unitList = event.getOptions().get(2).getAsString().toLowerCase();
            unitList = unitList.replace(", ", ",");
            StringTokenizer tokenizer = new StringTokenizer(unitList, ",");
            if (tokenizer.countTokens() > 15){
                MessageHelper.replyToMessage(event, "Too many units, max possible in system is 15");
            return;
        }

            List<String> units = new ArrayList<>();
            while (tokenizer.hasMoreTokens()){
                String unit = AliasHandler.resolveUnit(tokenizer.nextToken());
                String unitID = Mapper.getUnitID(unit, color);
                String unitPath = tile.getUnitPath(unitID);
                if (unitPath == null) {
                    MessageHelper.replyToMessage(event, "Unit: " + unit + " is not valid and not supported.");
                    return;
                }
                units.add(unitID);
            }
            int index = 0;
            for (String unit : units) {
                tile.setUnit(Integer.toString(index), unit);
                index++;
            }

            MapSaveLoadManager.saveMap(activeMap);

            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.ADD_UNITS, "Add units to map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Unit name/s. Example: DN, DN, CA")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Unit name/s. Example: DN, DN, CA")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: DN, DN, CA")
                                .setRequired(true))


        );
    }
}
