package ti4.commands.capture;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

abstract class CaptureUnitsSubcommand extends GameStateSubcommand {

    public CaptureUnitsSubcommand(String id, String description) {
        super(id, description, true, true);
        options();
    }

    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit' Eg. 2 infantry, carrier, 2 fighter, mech").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = Helper.getPlayerFromGame(game, event, event.getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        Tile tile = player.getNomboxTile();
        subExecute(event, tile);
    }

    public String getPlayerColor(GenericInteractionCreateEvent event) {
        Player player = getGame().getPlayer(event.getUser().getId());
        return player.getColor();
    }

    protected abstract void subExecute(SlashCommandInteractionEvent event, Tile tile);
}
