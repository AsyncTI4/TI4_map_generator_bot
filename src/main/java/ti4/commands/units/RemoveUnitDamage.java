package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;

public class RemoveUnitDamage extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        tile.removeUnitDamage(planetName, unitID, count);
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        tile.removeUnitDamage(planetName, unitID, count);
    }

    @Override
    public String getName() {
        return Constants.REMOVE_UNIT_DAMAGE;
    }

    @Override
    public String getDescription() {
        return "Remove unit damage from map";
    }
}
