package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetStatsAnchor extends PlayerSubcommandData {
    public SetStatsAnchor() {
        super(Constants.SET_STATS_ANCHOR, "Set the location your stats anchor appears on the map");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name or coordinate to anchor stats block").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        String tileID = null;
        if (tileOption != null) {
            tileID = StringUtils.substringBefore(tileOption.getAsString().toLowerCase(), " ");
        }

        if (!PositionMapper.isTilePositionValid(tileID)) {
            MessageHelper.sendMessageToEventChannel(event, "Tile ID `" + tileID + "` is not valid");
            return;
        }

        player.setPlayerStatsAnchorPosition(tileID);
        MessageHelper.sendMessageToEventChannel(event, tileID + " is now your stats anchor");
    }
}
