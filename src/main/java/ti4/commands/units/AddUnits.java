package ti4.commands.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
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
            new OptionData(OptionType.STRING, Constants.CC_USE, "\"t\"/\"tactic\" to add a token from tactic pool, \"r\"/\"retreat\" to add a token from reinforcements")
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.SLING_RELAY, "Declare use of and exhaust Sling Relay technology"),
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
        UnitHolder space = tile.getUnitHolders().get("space");
        boolean doesTileHaveFloatingGF = false;
        if(space != null && getPlayer().getColor() != null){
            doesTileHaveFloatingGF = space.getUnitCount(UnitType.Mech, getPlayer()) > 0 || space.getUnitCount(UnitType.Infantry, getPlayer()) > 0;
        }
        AddUnitService.addUnits(event, tile, game, color, unitList);
        if(space != null && getPlayer().getColor() != null && !doesTileHaveFloatingGF && ButtonHelper.getOtherPlayersWithShipsInTheSystem(getPlayer(), game, tile).isEmpty()){
            doesTileHaveFloatingGF = space.getUnitCount(UnitType.Mech, getPlayer()) > 0 || space.getUnitCount(UnitType.Infantry, getPlayer()) > 0;
            if(doesTileHaveFloatingGF){
                List<Button> buttons = ButtonHelper.getLandingTroopsButtons(getPlayer(), game, event, tile);
                Button concludeMove = Buttons.red(getPlayer().getFinsFactionCheckerPrefix() + "deleteButtons", "Done Landing Troops");
                buttons.add(concludeMove);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), getPlayer().getRepresentation()+" you can use these buttons to land troops if necessary",buttons);
            }
        }
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
