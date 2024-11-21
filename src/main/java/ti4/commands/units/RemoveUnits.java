package ti4.commands.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;

public class RemoveUnits extends GameStateCommand {

    public RemoveUnits() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.REMOVE_UNITS;
    }

    @Override
    public String getDescription() {
        return "Remove units from map";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units."),
            new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String color = CommandHelper.getColor(game, event);
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color/Faction not valid");
            return;
        }

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        boolean prioritizeNoDamage = event.getOption(Constants.PRIORITY_NO_DAMAGE, false, OptionMapping::getAsBoolean);
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        removeUnits(event, tile, game, color, unitList, prioritizeNoDamage);

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    static void removeUnits(SlashCommandInteractionEvent event, Tile tile, Game game, String color, String unitList, boolean prioritizeNoDamage) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            removeUnit(event, tile, game, parsedUnit, prioritizeNoDamage);
        }
    }

    private static void removeUnit(GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit, boolean prioritizeNoDamage) {
        UnitHolder unitHolder = determineUnitHolder(tile, parsedUnit);
        if (unitHolder == null) {
            handleNullUnitHolder(event, tile, parsedUnit);
            return;
        }

        int countToRemove = prioritizeNoDamage ? parsedUnit.getCount() : calculateDamageToRemoveWithDamage(unitHolder, parsedUnit);

        removeUnitsFromHolder(tile, unitHolder, parsedUnit, countToRemove);
        handleOtherUnitHoldersIfNeeded(tile, parsedUnit);

        tile.getUnitHolders().values().forEach(unitHolder_ ->
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unitHolder_.getName(), game)
        );
    }

    private static UnitHolder determineUnitHolder(Tile tile, ParsedUnit parsedUnit) {
        // Check if there is only one non-empty unit holder
        long nonEmptyUnitHolders = tile.getUnitHolders().values().stream()
            .filter(holder -> hasUnitsOrDamage(holder, parsedUnit.getUnitKey()))
            .count();

        if (nonEmptyUnitHolders == 1) {
            return tile.getUnitHolders().values().stream()
                .filter(holder -> hasUnitsOrDamage(holder, parsedUnit.getUnitKey()))
                .findFirst()
                .orElse(null);
        }

        return tile.getUnitHolders().get(parsedUnit.getLocation());
    }

    private static boolean hasUnitsOrDamage(UnitHolder holder, Units.UnitKey unitKey) {
        int unitCount = holder.getUnits().getOrDefault(unitKey, 0);
        int damageCount = holder.getUnitDamage().getOrDefault(unitKey, 0);
        return (unitCount + damageCount) > 0;
    }

    private static void handleNullUnitHolder(GenericInteractionCreateEvent event, Tile tile, ParsedUnit parsedUnit) {
        if (event instanceof ButtonInteractionEvent) {
            BotLogger.log(event.getId() + " found a null UnitHolder with the following info: " + tile.getRepresentation() + " " + parsedUnit.getLocation());
        }
    }

    private static int calculateDamageToRemoveWithDamage(UnitHolder unitHolder, ParsedUnit parsedUnit) {
        int unitCount = unitHolder.getUnits().getOrDefault(parsedUnit.getUnitKey(), 0);
        int damageCount = unitHolder.getUnitDamage().getOrDefault(parsedUnit.getUnitKey(), 0);
        return Math.max(0, damageCount - (unitCount - parsedUnit.getCount()));
    }

    private static void removeUnitsFromHolder(Tile tile, UnitHolder unitHolder, ParsedUnit parsedUnit, int countToRemove) {
        tile.removeUnit(unitHolder.getName(), parsedUnit.getUnitKey(), parsedUnit.getCount());
        tile.removeUnitDamage(unitHolder.getName(), parsedUnit.getUnitKey(), countToRemove);
    }

    private static void handleOtherUnitHoldersIfNeeded(Tile tile, ParsedUnit parsedUnit) {
        long totalUnitsOnHex = calculateTotalUnitsOnHex(tile, parsedUnit.getUnitKey());
        boolean otherHoldersContainUnit = checkOtherHoldersContainUnit(tile, parsedUnit);

        if (totalUnitsOnHex == parsedUnit.getCount() && otherHoldersContainUnit) {
            tile.getUnitHolders().forEach((name, holder) -> {
                if (!name.equals(parsedUnit.getLocation())) {
                    removeAllUnitsAndDamage(tile, name, parsedUnit.getUnitKey());
                }
            });
        }
    }

    private static long calculateTotalUnitsOnHex(Tile tile, Units.UnitKey unitKey) {
        return tile.getUnitHolders().values().stream()
            .mapToInt(holder ->
                holder.getUnits().getOrDefault(unitKey, 0) +
                holder.getUnitDamage().getOrDefault(unitKey, 0))
            .sum();
    }

    private static boolean checkOtherHoldersContainUnit(Tile tile, ParsedUnit parsedUnit) {
        return tile.getUnitHolders().values().stream()
            .filter(holder -> !holder.getName().equals(parsedUnit.getLocation()))
            .anyMatch(holder -> hasUnitsOrDamage(holder, parsedUnit.getUnitKey()));
    }

    private static void removeAllUnitsAndDamage(Tile tile, String unitHolderName, Units.UnitKey unitKey) {
        int unitCount = tile.getUnitHolders().get(unitHolderName).getUnits().getOrDefault(unitKey, 0);
        if (unitCount > 0) {
            tile.removeUnit(unitHolderName, unitKey, unitCount);
        }
        int damageCount = tile.getUnitHolders().get(unitHolderName).getUnitDamage().getOrDefault(unitKey, 0);
        if (damageCount > 0) {
            tile.removeUnitDamage(unitHolderName, unitKey, damageCount);
        }
    }
}
