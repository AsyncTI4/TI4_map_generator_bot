package ti4.discord.interactions.commands.units;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.tactical.TacticalActionService;
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
                new OptionData(
                                OptionType.STRING,
                                Constants.UNIT_NAMES,
                                "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                        .setRequired(true),
                new OptionData(
                                OptionType.STRING,
                                Constants.CC_USE,
                                "\"t\"/\"tactic\" to add a token from tactic pool, \"r\"/\"retreat\" to add a token from reinforcements")
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                        .setAutoComplete(true),
                new OptionData(
                        OptionType.BOOLEAN, Constants.SLING_RELAY, "Declare use of and exhaust Sling Relay technology"),
                new OptionData(
                        OptionType.BOOLEAN,
                        Constants.COEXIST,
                        "\"True\" to coexist; \"False\" (default) to not, and to start a combat etc. if another player is present"),
                new OptionData(
                        OptionType.BOOLEAN,
                        Constants.NO_MAPGEN,
                        "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        boolean coexist = event.getOption(Constants.COEXIST, Boolean.FALSE, OptionMapping::getAsBoolean);
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        UnitHolder space = tile.getSpaceUnitHolder();
        boolean doesTileHaveFloatingGF = false;
        if (space != null && getPlayer().getColor() != null) {
            doesTileHaveFloatingGF =
                    space.hasUnit(UnitType.Mech, getPlayer()) || space.hasUnit(UnitType.Infantry, getPlayer());
        }
        AddUnitService.addUnits(event, tile, game, color, unitList);
        if (space != null
                && getPlayer().getColor() != null
                && !doesTileHaveFloatingGF
                && ButtonHelper.getOtherPlayersWithShipsInTheSystem(getPlayer(), game, tile)
                        .isEmpty()) {
            doesTileHaveFloatingGF =
                    (space.hasUnit(UnitType.Mech, getPlayer()) || space.hasUnit(UnitType.Infantry, getPlayer()))
                            && tile.hasPlanets();
            if (doesTileHaveFloatingGF) {
                List<Button> buttons = TacticalActionService.getLandingTroopsButtons(game, getPlayer(), tile);
                Button concludeMove =
                        Buttons.red(getPlayer().factionButtonChecker() + "deleteButtons", "Done Landing Troops");
                buttons.add(concludeMove);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getChannel(),
                        getPlayer().toString() + " you can use these buttons to land troops if necessary",
                        buttons);
            }
        }
        if (!coexist) StartCombatService.combatCheck(game, event, tile);
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

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
