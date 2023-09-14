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
import ti4.map.*;
import ti4.message.MessageHelper;

public class AddUnits extends AddRemoveUnits {
    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color, Game activeGame) {
        tile.addUnit(planetName, unitID, count);
    }
    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, String unitID, String color, Game activeGame) {
        
        tile.addUnit(planetName, unitID, count);
        
    }

    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game activeGame) {
        OptionMapping option = event.getOption(Constants.CC_USE);
        if (option != null){
            String value = option.getAsString().toLowerCase();
            switch (value) {
                case "t/tactics", "t", "tactics", "tac", "tact" -> {
                    MoveUnits.removeTacticsCC(event, color, tile, GameManager.getInstance().getUserActiveGame(event.getUser().getId()));
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, activeGame, color);
                }
                case "r/retreat/reinforcements", "r", "retreat", "reinforcements" -> {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, activeGame, color);
                }
            }
        }
        OptionMapping optionSlingRelay = event.getOption(Constants.SLING_RELAY);
        if (optionSlingRelay != null){
            boolean useSlingRelay = optionSlingRelay.getAsBoolean();
            if (useSlingRelay) {
                String userID = event.getUser().getId();
                Player player = activeGame.getPlayer(userID);
                player = Helper.getGamePlayer(activeGame, player, event, null);
                player = Helper.getPlayer(activeGame, player, event);
                if (player != null) {
                    player.exhaustTech("sr");
                }
            }
        }
    }

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game activeGame) {
        tile = MoveUnits.flipMallice(event, tile, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return;
        }
        super.unitParsingForTile(event, color, tile, activeGame);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), activeGame);
        }
        
       
    }

    @Override
    public String getActionID() {
        return Constants.ADD_UNITS;
    }

    @Override
    protected String getActionDescription() {
        return "Add units to map";
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns").setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r - default is 'no'").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.BOOLEAN, Constants.SLING_RELAY, "Sling Relay Tech"))
                        .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"))
        );
    }
}
