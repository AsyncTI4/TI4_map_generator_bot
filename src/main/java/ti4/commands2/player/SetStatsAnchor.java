package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SetStatsAnchor extends GameStateSubcommand {

    public SetStatsAnchor() {
        super(Constants.SET_STATS_ANCHOR, "Set the location your stats anchor appears on the map", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name or coordinate to anchor stats block").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileID = StringUtils.substringBefore(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");

        if (!PositionMapper.isTilePositionValid(tileID)) {
            MessageHelper.sendMessageToEventChannel(event, "Tile ID `" + tileID + "` is not valid");
            return;
        }

        Player player = getPlayer();
        player.setPlayerStatsAnchorPosition(tileID);
        MessageHelper.sendMessageToEventChannel(event, tileID + " is now your stats anchor");
    }
}
