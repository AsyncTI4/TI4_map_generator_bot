package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.PlayAreaHelper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class RemoveAllUnits extends AddRemoveUnits {

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        tile.removeAllUnits(color);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            PlayAreaHelper.addPlanetToPlayArea(game, event, tile, unitHolder.getName());
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

    @Override
    public String getDescription() {
        return "Remove units from map";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                        .setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }
}
