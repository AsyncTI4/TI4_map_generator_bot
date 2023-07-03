package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class RemoveAllUnits extends AddRemoveUnits {

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Map activeMap) {
        tile.removeAllUnits(color);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder.getName(), activeMap);
        }
    }

    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        //No need for this action
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        //No need for this action
    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_ALL_UNITS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Remove units from map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.NO_MAPGEN, "'True' to not generate a map update with this command").setAutoComplete(true))
        );
    }

    @Override
    protected String getActionDescription() {
        return "";
    }
}
