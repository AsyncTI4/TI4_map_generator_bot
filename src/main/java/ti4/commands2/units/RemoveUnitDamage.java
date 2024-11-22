package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.ParseUnitService;

public class RemoveUnitDamage extends GameStateCommand {

    public RemoveUnitDamage() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.REMOVE_UNIT_DAMAGE;
    }

    @Override
    public String getDescription() {
        return "Remove unit damage from map";
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

        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        ParseUnitService.getParsedUnits(event, color, tile, unitList)
            .forEach(parsedUnit -> tile.removeUnitDamage(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount()));

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
