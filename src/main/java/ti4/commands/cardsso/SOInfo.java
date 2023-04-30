package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SOInfo extends SOCardsSubcommandData {
    public SOInfo() {
        super(Constants.INFO, "Sent scored and unscored Secret Objectives to your Cards Info thread");
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
        sendSecretObjectiveInfo(activeMap, player, event);
        sendMessage("SO Info Sent");
    }

    public static void sendSecretObjectiveInfo(Map activeMap, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(event, player) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendSecretObjectiveInfo(activeMap, player);
    }

    public static void sendSecretObjectiveInfo(Map activeMap, Player player, ButtonInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(event, player) + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendSecretObjectiveInfo(activeMap, player);
    }
    
    public static void sendSecretObjectiveInfo(Map activeMap, Player player) {
        //SO INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getSecretObjectiveCardInfo(activeMap, player));

        //BUTTONS
        String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
        List<Button> soButtons = getUnscoredSecretObjectiveButtons(activeMap, player);
        if (soButtons != null && !soButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, soButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    }   

    private static String getSecretObjectiveRepresentationShort(String soID) {
        return getSecretObjectiveRepresentationShort(soID, null);
    }
    private static String getSecretObjectiveRepresentationShort(String soID, Integer soUniqueID) {
        StringBuilder sb = new StringBuilder();
        String[] soSplit = Mapper.getSecretObjective(soID).split(";");
        String soName = soSplit[0];
        sb.append(Emojis.SecretObjective).append("__" + soName + "__").append("\n");
        return sb.toString();
    }

    private static String getSecretObjectiveRepresentation(String soID) {
        return getSecretObjectiveRepresentation(soID, null);
    }
    private static String getSecretObjectiveRepresentation(String soID, Integer soUniqueID) {
        StringBuilder sb = new StringBuilder();
        String[] soSplit = Mapper.getSecretObjective(soID).split(";");
        String soName = soSplit[0];
        String soPhase = soSplit[1];
        String soDescription = soSplit[2];
        sb.append(Emojis.SecretObjective).append("__**" + soName + "**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        return sb.toString();
    }

    private static String getSecretObjectiveCardInfo(Map activeMap, Player player) {
        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeMap.getScoredSecretObjective(player.getUserID()));
        for (String id : activeMap.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;

        //SCORED SECRET OBJECTIVES
        sb.append("**Scored Secret Objectives:**").append("\n");
        if (scoredSecretObjective != null) {
            if (scoredSecretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + so.getValue(), 4)).append(")`");
                    sb.append(getSecretObjectiveRepresentationShort(so.getKey()));
                    index++;
                }
            }
        }
        sb.append("\n");

        //UNSCORED SECRET OBJECTIVES
        sb.append("**Unscored Secret Objectives:**").append("\n");
        if (secretObjective != null) {
            if (secretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                    String[] soSplit = Mapper.getSecretObjective(so.getKey()).split(";");
                    String soName = soSplit[0];
                    Integer idValue = so.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + idValue, 4)).append(")`");
                    sb.append(getSecretObjectiveRepresentation(so.getKey()));
                    index++;
                }
            }
        }
        return sb.toString();
    }

    private static List<Button> getUnscoredSecretObjectiveButtons(Map activeMap, Player player) {
        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        List<Button> soButtons = new ArrayList<>();
        if (secretObjective != null && !secretObjective.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                String[] soSplit = Mapper.getSecretObjective(so.getKey()).split(";");
                String soName = soSplit[0];
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Button.primary(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName).withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
                }
            }
        }
        return soButtons;
    }
}
