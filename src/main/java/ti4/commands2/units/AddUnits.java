package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PlayAreaHelper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class AddUnits extends AddRemoveUnits {

    @Override
    protected void after() {
        OptionMapping option = event.getOption(Constants.CC_USE);
        if (option != null) {
            String value = option.getAsString().toLowerCase();
            if (!event.getInteraction().getName().equals(Constants.MOVE_UNITS)) {
                switch (value) {
                    case "t/tactics", "t", "tactics", "tac", "tact" -> {
                        MoveUnits.removeTacticsCC(event, color, tile, game);
                        CommandCounterHelper.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, game, color);
                    }
                    case "r/retreat/reinforcements", "r", "retreat", "reinforcements" -> {
                        CommandCounterHelper.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, game, color);
                    }
                }
            }
        }
        OptionMapping optionSlingRelay = event.getOption(Constants.SLING_RELAY);
        if (optionSlingRelay != null) {
            boolean useSlingRelay = optionSlingRelay.getAsBoolean();
            if (useSlingRelay) {
                if (player != null) {
                    player.exhaustTech("sr");
                }
            }
        }
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        tile.addUnit(planetName, unitID, count);
    }

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        tile = MoveUnits.flipMallice(event, tile, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return;
        }
        super.unitParsingForTile(event, color, tile, game);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            PlayAreaHelper.addPlanetToPlayArea(game, event, tile, unitHolder_.getName());
        }
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
}
