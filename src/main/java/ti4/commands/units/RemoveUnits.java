package ti4.commands.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.unit.RemoveUnitService;

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
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.PRIORITIZE_DAMAGED, "Priority for not damaged units."),
            new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        boolean prioritizeDamaged = event.getOption(Constants.PRIORITIZE_DAMAGED, false, OptionMapping::getAsBoolean);
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        RemoveUnitService.removeUnits(event, tile, game, color, unitList, prioritizeDamaged);

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
