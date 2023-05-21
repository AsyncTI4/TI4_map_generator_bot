package ti4.commands.explore;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicInfo extends ExploreSubcommandData {
    public RelicInfo() {
		super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel");
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
        sendRelicInfo(activeMap, player, event);
    }

    public static void sendRelicInfo(Map activeMap, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeMap) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendRelicInfo(activeMap, player);
    }

    public static void sendRelicInfo(Map activeMap, Player player) {
        //RELIC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getRelicInfoText(player));

        //BUTTONS
        String purgeRelicMessage = "_ _\nClick a button below to exhaust or purge a Relic";
        List<Button> relicButtons = getRelicButtons(activeMap, player);
        if (relicButtons != null && !relicButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(purgeRelicMessage, relicButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    } 
    
    private static String getRelicInfoText(Player player) {
        List<String> playerRelics = player.getRelics();
        StringBuilder sb = new StringBuilder("__**Relic Info**__\n");
        if (playerRelics == null || playerRelics.isEmpty()) {
            sb.append("> No Relics");
            return sb.toString();
        }
        for (String relicID : playerRelics) {
            sb.append(Helper.getRelicRepresentation(relicID));
        }
        return sb.toString();
    }

    private static List<Button> getRelicButtons(Map activeMap, Player player) {
        return null;
    }
}
