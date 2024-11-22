package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class MoveUnits extends GameStateCommand {

    public MoveUnits() {
        super(true, false);
    }

    @Override
    public String getName() {
        return Constants.MOVE_UNITS;
    }

    @Override
    public String getDescription() {
        return "Move units from one system to another system";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile to move units from")
                .setRequired(true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile to move units to")
                .setAutoComplete(true)
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.UNIT_NAMES_TO, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.CC_USE, "Type t or tactics to add a CC from tactics, r or retreat to add a CC without taking it from tactics")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units. Type in yes or y"),
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

        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        Tile tileFrom = CommandHelper.getTile(event, game);
        if (tileFrom == null) {
            BotLogger.log("Could not find the tile you're moving from.");
            return;
        }

        Tile tileTo = CommandHelper.getTile(event, game, event.getOption(Constants.TILE_NAME_TO).getAsString());
        if (tileTo == null) {
            BotLogger.log("Could not find the tile you're moving to.");
            return;
        }

        boolean prioritizeNoDamage = event.getOption(Constants.PRIORITY_NO_DAMAGE, false, OptionMapping::getAsBoolean);
        String fromUnitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        RemoveUnitService.removeUnits(event, tileFrom, game, color, fromUnitList, prioritizeNoDamage);

        String toUnitList = event.getOption(Constants.UNIT_NAMES_TO).getAsString();
        AddUnitService.addUnits(event, tileTo, game, color, toUnitList);

        UnitCommandHelper.handleCcUseOption(event, tileTo, color, game);
        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
