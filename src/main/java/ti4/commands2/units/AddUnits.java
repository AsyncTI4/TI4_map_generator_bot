package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.AddUnitService;

public class AddUnits extends GameStateCommand {

    public AddUnits() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.ADD_UNITS;
    }

    @Override
    public String getDescription() {
        return "Add units to map";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                .setRequired(true),
            new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r - default is 'no'")
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.SLING_RELAY, "Declare use of and exhaust Sling Relay Tech"),
            new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();

        AddUnitService.addUnits(event, tile, game, color, unitList);

        StartCombatService.combatCheck(game, event, tile);
        handleSlingRelayOption(event);
        UnitCommandHelper.handleCcUseOption(event, tile, color, game);
        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    private void handleSlingRelayOption(SlashCommandInteractionEvent event) {
        OptionMapping optionSlingRelay = event.getOption(Constants.SLING_RELAY);
        if (optionSlingRelay == null) {
            return;
        }
        boolean useSlingRelay = optionSlingRelay.getAsBoolean();
        if (!useSlingRelay) {
            return;
        }
        getPlayer().exhaustTech("sr");
    }
}
