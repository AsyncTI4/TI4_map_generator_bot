package ti4.discord.interactions.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;

class CaptureUnits extends GameStateSubcommand {

    private final boolean remove;
    private final boolean buildUnitKeyDirectly;

    private CaptureUnits(String name, String description, boolean remove, boolean buildUnitKeyDirectly) {
        super(name, description, true, true);
        this.remove = remove;
        this.buildUnitKeyDirectly = buildUnitKeyDirectly;
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.UNIT_NAMES,
                        "Comma separated list of '{count} unit' Eg. 2 infantry, carrier, 2 fighter, mech")
                .setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor("Faction or Color for unit"));
        addOptions(CommonOptions.factionColor("Faction or Color capturing (default you)"));
        addOptions(CommonOptions.noMapGen());
    }

    static CaptureUnits add() {
        return new CaptureUnits(Constants.ADD_UNITS, "Capture units", false, false);
    }

    static CaptureUnits remove() {
        return new CaptureUnits(Constants.REMOVE_UNITS, "Release captured units", true, false);
    }

    static CaptureUnits fixRemove() {
        return new CaptureUnits("fix_remove_units", "Release captured units", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String color = UnitCommandHelper.getTargetColor(event, game);
        if (color == null) return;

        Tile tile = getPlayer().getNomboxTile();

        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        for (ParsedUnit parsedUnit : ParseUnitService.getParsedUnits(event, color, tile, unitList)) {
            // fighters and infantry are added as your own color
            Units.UnitType unitType = parsedUnit.unitKey().unitType();
            if (unitType == Units.UnitType.Fighter || unitType == Units.UnitType.Infantry) {
                String ownColor = getPlayer().getColor();
                Units.UnitKey unitKey = buildUnitKeyDirectly
                        ? new Units.UnitKey(unitType, ownColor)
                        : Mapper.getUnitKey(unitType.toString(), ownColor);
                parsedUnit = new ParsedUnit(unitKey, parsedUnit.count(), "space");
            }
            if (remove) {
                tile.removeUnit("space", parsedUnit.unitKey(), parsedUnit.count());
            } else {
                tile.addUnit("space", parsedUnit.unitKey(), parsedUnit.count());
            }
        }

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
