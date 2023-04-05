package ti4.commands.cards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ACInfo extends CardsSubcommandData {
    public ACInfo() {
        super(Constants.INFO + 2, "Send Action Cards to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        String headerText = Helper.getPlayerRepresentation(event, player) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendActionCardInfo(activeMap, player);
        sendMessage("AC Info Sent");
    }

    public static void sendActionCardInfo(Map activeMap, Player player) {
        //CARDS INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getActionCardInfo(activeMap, player));

        //BUTTONS
        String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
        List<Button> acButtons = getPlayActionCardButtons(activeMap, player);
        List<MessageCreateData> messageList = MessageHelper.getMessageObject(secretScoreMsg, acButtons);
        ThreadChannel cardsInfoThreadChannel = Helper.getPlayerCardsInfoThread(activeMap, player);
        for (MessageCreateData message : messageList) {
            cardsInfoThreadChannel.sendMessage(message).queue();
        }
    } 

    private static String getActionCardInfo(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");


        //ACTION CARDS
        sb.append("**Action Cards:**").append("\n");
        int index = 1;

        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            if (actionCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                    String[] acSplit = Mapper.getActionCard(ac.getKey()).split(";");
                    String acName = acSplit[0];
                    String acPhase = acSplit[1];
                    String acWindow = acSplit[2];
                    String acDescription = acSplit[3];
                    Integer value = ac.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    sb.append(Emojis.ActionCard).append("__**" + acName + "**__").append(" *(").append(acPhase).append(" Phase)*: _").append(acWindow).append(":_ ").append(acDescription).append("\n");
                    index++;
                }
            }
        }

        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Map activeMap, Player player) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
}
