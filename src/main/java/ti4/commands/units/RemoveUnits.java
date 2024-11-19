package ti4.commands.units;

import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;

public class RemoveUnits extends AddRemoveUnits {

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        removeStuff(event, tile, count, planetName, unitID, color, true, game);
    }

    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        OptionMapping option = event.getOption(Constants.PRIORITY_NO_DAMAGE);
        boolean priorityDmg = true;
        if (option != null) {
            String value = option.getAsString().toLowerCase();
            if ("yes".equals(value) || "y".equals(value)) {
                priorityDmg = false;
            }
        }
        removeStuff(event, tile, count, planetName, unitID, color, priorityDmg, game);
    }

    public void removeStuff(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, boolean priorityDmg, Game game) {
        int countToRemove = 0;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);

        // Check for space unit holder when only single stack of unit is present anywhere on the tile
        // This allows for removes like "2 infantry" when they are the only infantry on a planet
        long nonEmptyUnitHolders = tile.getUnitHolders().values().stream()
            .filter(m -> m.getUnits().getOrDefault(unitID, 0) + m.getUnitDamage().getOrDefault(unitID, 0) > 0)
            .count();

        // These calcluations will let us know if we are in a scenario where we can remove all of a particular unit from
        // the hex
        // This allows for moves like "2 infantry" when there's a hex with 0 in space and 1 infantry on each of 2 planets
        long totalUnitsOnHex = tile.getUnitHolders().values().stream()
            .mapToInt(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID, 0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID, 0))
            .sum();

        boolean otherUnitHoldersContainUnit = tile.getUnitHolders().values().stream()
            .filter(planetTemp -> !Objects.equals(planetTemp.getName(), planetName))
            .anyMatch(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID, 0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID, 0) > 0);

        if (nonEmptyUnitHolders == 1) {
            unitHolder = tile.getUnitHolders().values().stream()
                .filter(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID, 0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID, 0) > 0).findFirst().orElse(null);

        }
        if (unitHolder == null) {
            unitHolder = tile.getUnitHolders().get(planetName);
        }

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

        if (unitHolder != null) {
            tile.removeUnit(unitHolder.getName(), unitID, count);
            tile.removeUnitDamage(unitHolder.getName(), unitID, countToRemove);
        } else {
            if (event instanceof ButtonInteractionEvent) {
                BotLogger.log(event.getId() + " found a null unitholder with the following info: " + tile.getRepresentation() + " " + planetName);
            }
        }

        // Check to see if we should remove from other unitHolders
        if ((totalUnitsOnHex == count) && otherUnitHoldersContainUnit) {
            for (String unitHolderName : tile.getUnitHolders().keySet()) {
                if (!unitHolderName.equals(planetName)) {
                    int tempCount = tile.getUnitHolders().get(unitHolderName).getUnits().getOrDefault(unitID, 0);
                    if (tempCount != 0) {
                        tile.removeUnit(unitHolderName, unitID, tempCount);
                    }
                    tempCount = tile.getUnitHolders().get(unitHolderName).getUnitDamage().getOrDefault(unitID, 0);
                    if (tempCount != 0) {
                        tile.removeUnitDamage(unitHolderName, unitID, tempCount);
                    }
                }
            }
        }
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), game);
        }
    }

    @Override
    public String getName() {
        return Constants.REMOVE_UNITS;
    }

    @Override
    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        super.actionAfterAll(event, tile, color, game);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), game);
        }
    }

    @Override
    protected String getActionDescription() {
        return "Remove units from map";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units. Type in yes or y"))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }
}
