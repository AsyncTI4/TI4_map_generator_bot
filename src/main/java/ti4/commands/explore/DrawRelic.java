package ti4.commands.explore;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawRelic extends GenericRelicAction {

    public DrawRelic() {
        super(Constants.RELIC_DRAW, "Draw a relic");
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        drawRelicAndNotify(player, event, activeMap);
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Map activeMap) {
        String relicID = activeMap.drawRelic();
        if (relicID.isEmpty()) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getMessageChannel(), "Relic deck is empty");
            return;
        }
        player.addRelic(relicID);
        String[] relicData = Mapper.getRelic(relicID).split(";");
        StringBuilder message = new StringBuilder();
        message.append(Helper.getPlayerRepresentation(player, activeMap)).append(" drew a Relic:\n").append(Emojis.Relic).append(" __**").append(relicData[0]).append("**__\n> ").append(relicData[1]).append("\n");
       
        //Append helpful commands after relic draws and resolve effects:
        switch (relicID) {
            case "nanoforge" -> {
                message.append("Run the following commands to use Nanoforge:\n")
                       .append("     `/explore relic_purge relic: nanoforge`\n")
                       .append("     `/add_token token:nanoforge tile_name:{TILE} planet_name:{PLANET}`");
            }
            case "shard" -> {
                Integer poIndex = activeMap.addCustomPO("Shard of the Throne", 1);
                activeMap.scorePublicObjective(player.getUserID(), poIndex);
                message.append("Custom PO 'Shard of the Throne' has been added.\n")
                       .append(Helper.getPlayerRepresentation(player, activeMap)).append(" scored 'Shard of the Throne'");
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer poIndex = activeMap.addCustomPO(customPOName, 1);
                activeMap.scorePublicObjective(player.getUserID(), poIndex);
                message.append("Custom PO '" + customPOName + "' has been added.\n")
                       .append(Helper.getPlayerRepresentation(player, activeMap)).append(" scored '" + customPOName + "'");
            }
        }
        if (activeMap.isFoWMode())
        {
            FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, message.toString());
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
    }
}
