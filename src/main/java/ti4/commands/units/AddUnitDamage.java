package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

import ti4.map.Tile;

public class AddUnitDamage extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        tile.addUnitDamage(planetName, unitID, count);
    }
    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        tile.addUnitDamage(planetName, unitID, count);
    }

    @Override
    public String getActionID() {
        return Constants.ADD_UNIT_DAMAGE;
    }


    @Override
    protected String getActionDescription() {
        return "Add unit damage to map";
    }


}
