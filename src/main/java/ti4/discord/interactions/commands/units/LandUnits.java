package ti4.discord.interactions.commands.units;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class LandUnits extends GameStateCommand {

    public LandUnits() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.LAND_UNITS;
    }

    @Override
    public String getDescription() {
        return "Land units on a planet. They can be floating in space or nonexistent before running this command.";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.PLANET, "Planet")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(
                                OptionType.STRING,
                                Constants.UNIT_NAMES,
                                "Comma separated list of '{count} unit ' Eg. 2 infantry")
                        .setRequired(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                        .setAutoComplete(true),
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
        String planetID = event.getOption(Constants.PLANET).getAsString();
        planetID = AliasHandler.resolvePlanet(
                StringUtils.substringBefore(planetID, " (").replace(" ", ""));
        Tile tile = game.getTileFromPlanet(planetID);
        if (tile == null) return;

        String color = getPlayer().getColor();
        boolean coexist = event.getOption(Constants.COEXIST, Boolean.FALSE, OptionMapping::getAsBoolean);
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString();
        boolean doesTileHaveFloatingGF = false;
        UnitHolder space = tile.getUnitHolders().get("space");
        if (space != null && getPlayer().getColor() != null) {
            doesTileHaveFloatingGF = space.getUnitCount(UnitType.Mech, getPlayer()) > 0
                    || space.getUnitCount(UnitType.Infantry, getPlayer()) > 0;
        }
        for (String unitString : unitList.split(",")) {
            if (doesTileHaveFloatingGF) {
                RemoveUnitService.removeUnits(event, tile, game, color, unitString.strip());
            }
            AddUnitService.addUnits(event, tile, game, color, unitString.strip() + " " + planetID);
        }

        doesTileHaveFloatingGF = false;
        space = tile.getUnitHolders().get("space");
        if (space != null && getPlayer().getColor() != null) {
            doesTileHaveFloatingGF = space.getUnitCount(UnitType.Mech, getPlayer()) > 0
                    || space.getUnitCount(UnitType.Infantry, getPlayer()) > 0;
        }

        if (space != null
                && getPlayer().getColor() != null
                && !doesTileHaveFloatingGF
                && ButtonHelper.getOtherPlayersWithShipsInTheSystem(getPlayer(), game, tile)
                        .isEmpty()) {
            doesTileHaveFloatingGF = space.getUnitCount(UnitType.Mech, getPlayer()) > 0
                    || space.getUnitCount(UnitType.Infantry, getPlayer()) > 0;
            if (doesTileHaveFloatingGF) {
                List<Button> buttons = TacticalActionService.getLandingTroopsButtons(game, getPlayer(), tile);
                Button concludeMove =
                        Buttons.red(getPlayer().factionButtonChecker() + "deleteButtons", "Done Landing Troops");
                buttons.add(concludeMove);
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getChannel(),
                        getPlayer().getRepresentation() + " you can use these buttons to land troops if necessary",
                        buttons);
            }
        }
        if (!coexist) StartCombatService.combatCheck(game, event, tile);
        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
