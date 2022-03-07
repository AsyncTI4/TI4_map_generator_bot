package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

abstract public class AddRemoveUnits implements Command{

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

            String color = event.getOptions().get(0).getAsString().toLowerCase();
            if (!Mapper.isColorValid(color)) {
                MessageHelper.replyToMessage(event, "Color not valid");
                return;
            }

            String tileID = AliasHandler.resolveTile(event.getOptions().get(1).getAsString().toLowerCase());
            Map activeMap = mapManager.getUserActiveMap(userID);
            if (activeMap.isTileDuplicated(tileID)){
                MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
                return;
            }
            Tile tile = activeMap.getTile(tileID);
            if (tile == null){
                tile = activeMap.getTileByPostion(tileID);
            }
            if (tile == null) {
                MessageHelper.replyToMessage(event, "Tile in map not found");
                return;
            }

            unitParsingForTile(event, color, tile);
            MapSaveLoadManager.saveMap(activeMap);

            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
        String unitList = event.getOptions().get(2).getAsString().toLowerCase();
        unitList = unitList.replace(", ", ",");
        StringTokenizer tokenizer = new StringTokenizer(unitList, ",");
        while (tokenizer.hasMoreTokens()) {
            StringTokenizer unitInfoTokenizer = new StringTokenizer(tokenizer.nextToken(), " ");

            int count = 1;
            boolean numberIsSet = false;
            String planetName = Constants.SPACE;
            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }
            String unitID = Mapper.getUnitID(unit, color);
            String unitPath = tile.getUnitPath(unitID);
            if (unitPath == null) {
                MessageHelper.replyToMessage(event, "Unit: " + unit + " is not valid and not supported.");
            }
            if (unitInfoTokenizer.hasMoreTokens()) {
                planetName = AliasHandler.resolvePlanet(unitInfoTokenizer.nextToken());
            }
            unitAction(tile, count, planetName, unitID);
        }
    }

    abstract protected void unitAction(Tile tile, int count, String planetName, String unitID);

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    abstract protected String getActionID();


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color: red, green etc.")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))


        );
    }

    abstract protected String getActionDescription();


}
