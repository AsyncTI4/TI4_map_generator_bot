package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class SetStatsAnchor extends GameStateSubcommand {

    public SetStatsAnchor() {
        super(Constants.SET_STATS_ANCHOR, "Set the location your stats anchor appears on the map", true, true);
        addOptions(CommonOptions.tileName("Tile name or coordinate to anchor stats block"));
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileID = StringUtils.substringBefore(
                event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");

        if (!PositionMapper.isTilePositionValid(getGame(), tileID)) {
            MessageHelper.sendMessageToEventChannel(event, "Tile ID `" + tileID + "` is not valid");
            return;
        }

        Player player = getPlayer();
        player.setPlayerStatsAnchorPosition(tileID);
        MessageHelper.sendMessageToEventChannel(event, tileID + " is now your stats anchor");
    }
}
