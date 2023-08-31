package ti4.commands.player;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public class SetStatsAnchor extends PlayerSubcommandData {
        public SetStatsAnchor() {
        super(Constants.SET_STATS_ANCHOR, "Set the location your stats anchor appears on the map");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name or coordinate to anchor stats block").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,"Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player player = activeMap.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeMap, player, event, null);
		player = Helper.getPlayer(activeMap, player, event);
		if (player == null) {
			sendMessage("Player could not be found");
			return;
		}

        
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        String tileID = null;
        if (tileOption != null) {
            tileID = StringUtils.substringBefore(tileOption.getAsString().toLowerCase(), " ");
        }
        
        if (!PositionMapper.isTilePositionValid(tileID)) {
            sendMessage("Tile ID `" + tileID + "` is not valid");
            return;
        }

        player.setPlayerStatsAnchorPosition(tileID);
        sendMessage(tileID + " is now your stats anchor");
    }
}
