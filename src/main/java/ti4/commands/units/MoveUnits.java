package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.tokens.AddCC;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class MoveUnits extends AddRemoveUnits {

    private boolean toAction = false;

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
        toAction = false;
        String unitList = event.getOptions().get(2).getAsString().toLowerCase();
        unitParsing(event, color, tile, unitList);

        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();

        String tileID = AliasHandler.resolveTile(event.getOptions().get(3).getAsString().toLowerCase());
        Map activeMap = mapManager.getUserActiveMap(userID);
        tile = getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile: " + tileID + " not found");
            return;
        }

        toAction = true;
        unitList = event.getOptions().get(4).getAsString().toLowerCase();
        unitParsing(event, color, tile, unitList);

        OptionMapping option = event.getOption(Constants.CC);
        if (option == null) {
          AddCC.addCC(event, color, tile);
        }
    }

    @Override
    protected void unitAction(Tile tile, int count, String planetName, String unitID) {
        if (toAction) {
            tile.addUnit(planetName, unitID, count);
        } else {
            tile.removeUnit(planetName, unitID, count);
        }
    }

    @Override
    protected String getActionDescription() {
        return "Move units from one system to another system";
    }

    @Override
    public String getActionID() {
        return Constants.MOVE_UNITS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color: red, green etc.")
                                .setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "From System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "To System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES_TO, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CC, "Type no to not add CC"))
        );
    }
}
