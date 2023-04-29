package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.map.Tile;

public class RemoveUnitDamage extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        tile.removeUnitDamage(planetName, unitID, count);
    }
    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        tile.removeUnitDamage(planetName, unitID, count);
    }


    @Override
    public String getActionID() {
        return Constants.REMOVE_UNIT_DAMAGE;
    }

    @Override
    protected String getActionDescription() {
        return "Remove unit damage from map";
    }
}
