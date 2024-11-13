package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.tokens.AddCC;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class AddUnits extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        tile.addUnit(planetName, unitID, count);
        actionAfterAll(event, tile, color, game);
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        tile.addUnit(planetName, unitID, count);
    }

    public void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        OptionMapping option = event.getOption(Constants.CC_USE);
        if (option != null) {
            String value = option.getAsString().toLowerCase();
            if (!event.getInteraction().getName().equals(Constants.MOVE_UNITS)) {
                switch (value) {
                    case "t/tactics", "t", "tactics", "tac", "tact" -> {
                        MoveUnits.removeTacticsCC(event, color, tile, getGame();)
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, game, color);
                    }
                    case "r/retreat/reinforcements", "r", "retreat", "reinforcements" -> {
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, game, color);
                    }
                }
            }
        }
        OptionMapping optionSlingRelay = event.getOption(Constants.SLING_RELAY);
        if (optionSlingRelay != null) {
            boolean useSlingRelay = optionSlingRelay.getAsBoolean();
            if (useSlingRelay) {
                String userID = event.getUser().getId();
                Player player = game.getPlayer(userID);
                player = Helper.getGamePlayer(game, player, event, null);
                player = Helper.getPlayerFromEvent(game, player, event);
                if (player != null) {
                    player.exhaustTech("sr");
                }
            }
        }
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
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), game);
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r - default is 'no'").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.SLING_RELAY, "Declare use of and exhaust Sling Relay Tech"))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }
}
