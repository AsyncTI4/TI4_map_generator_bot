package ti4.commands.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;

//TODO: DELETE THIS AFTER DONE
class FixRemoveCaptureUnits extends GameStateSubcommand {

    public FixRemoveCaptureUnits() {
        super(Constants.REMOVE_UNITS, "Release captured units", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit' Eg. 2 infantry, carrier, 2 fighter, mech").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color for unit").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color capturing (default you)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String color = UnitCommandHelper.getTargetColor(event, game);
        if (color == null) return;

        Tile tile = getPlayer().getNomboxTile();

        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            // fighters and infantry are added as your own color
            if (parsedUnit.getUnitKey().getUnitType().equals(Units.UnitType.Fighter) ||
                    parsedUnit.getUnitKey().getUnitType().equals(Units.UnitType.Infantry)) {
                Units.UnitKey unitKey = new Units.UnitKey(parsedUnit.getUnitKey().getUnitType(), getPlayer().getColor());
                parsedUnit = new ParsedUnit(unitKey, parsedUnit.getCount(), "space");
            }
            tile.removeUnit("space", parsedUnit.getUnitKey(), parsedUnit.getCount());
        }

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
