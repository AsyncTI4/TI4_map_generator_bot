package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class RemoveAllUnits extends GameStateCommand {

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        tile.removeAllUnits(color);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder.getName(), game);
        }
    }

    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        //No need for this action
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        //No need for this action
    }

    @Override
    public String getName() {
        return Constants.REMOVE_ALL_UNITS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), "Remove units from map")
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }

    @Override
    protected String getActionDescription() {
        return "";
    }
}
