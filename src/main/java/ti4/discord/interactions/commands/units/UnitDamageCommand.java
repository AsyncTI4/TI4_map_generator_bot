package ti4.discord.interactions.commands.units;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.ParsedUnit;

public class UnitDamageCommand extends GameStateCommand {

    private final boolean remove;

    private UnitDamageCommand(String name, String description, boolean remove) {
        super(name, description, true, true);
        this.remove = remove;
    }

    public static UnitDamageCommand add() {
        return new UnitDamageCommand(Constants.ADD_UNIT_DAMAGE, "Add unit damage to map", false);
    }

    public static UnitDamageCommand remove() {
        return new UnitDamageCommand(Constants.REMOVE_UNIT_DAMAGE, "Remove unit damage from map", true);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                CommonOptions.tileName(),
                new OptionData(
                                OptionType.STRING,
                                Constants.UNIT_NAMES,
                                "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                        .setRequired(true),
                CommonOptions.factionColor("Faction or Color for unit"),
                CommonOptions.noMapGen());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        for (ParsedUnit parsedUnit : ParseUnitService.getParsedUnits(event, color, tile, unitList)) {
            if (remove) {
                tile.removeUnitDamage(parsedUnit.location(), parsedUnit.unitKey(), parsedUnit.count());
            } else {
                tile.addUnitDamage(parsedUnit.location(), parsedUnit.unitKey(), parsedUnit.count());
            }
        }

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
