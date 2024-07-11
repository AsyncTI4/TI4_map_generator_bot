package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class SetHomeSystemPosition extends FrankenSubcommandData {

    public SetHomeSystemPosition() {
        super(Constants.SET_HOMESYSTEM_POS, "Set home system position to override other checks.");
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "Home system tile. Enter 'none' to reset to default.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        String hsTileString = event.getOption(Constants.HS_TILE_POSITION, null, OptionMapping::getAsString);
        if ("none".equalsIgnoreCase(hsTileString)) {
            player.setHomeSystemPosition(null);
            MessageHelper.sendMessageToEventChannel(event, "Home system reset");
        } else {
            Tile hsTile = game.getTileByPosition(hsTileString);
            if (hsTile == null) {
                MessageHelper.sendMessageToEventChannel(event, "Invalid tile position.");
            } else {
                player.setHomeSystemPosition(hsTileString);
                player.setPlayerStatsAnchorPosition(hsTileString);
                MessageHelper.sendMessageToEventChannel(event, "Home system set to " + hsTile.getRepresentation() + " for " + player.getRepresentation());
            }
        }
    }
}
