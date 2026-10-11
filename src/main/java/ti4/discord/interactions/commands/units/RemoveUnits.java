package ti4.discord.interactions.commands.units;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.service.unit.RemoveUnitService;

public class RemoveUnits extends GameStateCommand {

    public RemoveUnits() {
        super(Constants.REMOVE_UNITS, "Remove units from map", true, true);
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
                new OptionData(OptionType.BOOLEAN, Constants.PRIORITIZE_DAMAGED, "Priority for not damaged units."),
                CommonOptions.noMapGen());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        boolean prioritizeDamaged =
                event.getOption(Constants.PRIORITIZE_DAMAGED, Boolean.FALSE, OptionMapping::getAsBoolean);
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        RemoveUnitService.removeUnits(event, tile, game, color, unitList, prioritizeDamaged);

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
