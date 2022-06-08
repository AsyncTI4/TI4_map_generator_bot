package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class RemoveUnits extends AddRemoveUnits {

    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID) {
        OptionMapping option = event.getOption(Constants.PRIORITY_NO_DAMAGE);
        boolean priorityDmg = true;
        if (option != null){
            String value = option.getAsString().toLowerCase();
            if ("yes".equals(value) || "y".equals(value))
            {
                priorityDmg = false;
            }
        }

        int countToRemove = 0;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        if (!priorityDmg) {
            Integer unitCountInSystem = unitHolder.getUnits().get(unitID);
            if (unitCountInSystem != null) {
                Integer unitDamageCountInSystem = unitHolder.getUnitDamage().get(unitID);
                if (unitDamageCountInSystem != null) {
                    countToRemove = unitDamageCountInSystem - (unitCountInSystem - count);

                }
            }
        } else {
            countToRemove = count;
        }
        tile.removeUnit(planetName, unitID, count);
        tile.removeUnitDamage(planetName, unitID, countToRemove);

    }

    @Override
    public String getActionID() {
        return Constants.REMOVE_UNITS;
    }

    @Override
    protected String getActionDescription() {
        return "Remove units from map";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                                .setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units. Type in yes or y"))
        );
    }
}
