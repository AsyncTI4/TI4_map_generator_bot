package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
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

public class PNInfo extends PNCardsSubcommandData {
    public PNInfo() {
        super(Constants.INFO, "Send your Promissory Notes to your Cards Info thread");
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
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, Helper.getPlayerRepresentation(event, player));
        sendPromissoryNoteInfo(activeMap, player);
        sendMessage("PN Info Sent");
    }

    public static void sendPromissoryNoteInfo(Map activeMap, Player player) {
        //CARDS INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getPromissoryNoteCardInfo(activeMap, player));

        //BUTTONS
        String pnPlayMessage = "_ _\nClick a button below to play a Promissory Note";
        List<Button> pnButtons = getPlayablePNButtons(activeMap, player);
        List<MessageCreateData> messageList = MessageHelper.getMessageObject(pnPlayMessage, pnButtons);
        ThreadChannel cardsInfoThreadChannel = Helper.getPlayerCardsInfoThread(activeMap, player);
        for (MessageCreateData message : messageList) {
            cardsInfoThreadChannel.sendMessage(message).queue();
        }
    }

    private static List<Button> getPlayablePNButtons(Map activeMap, Player player) {
        return null;
    }

    public static String getPromissoryNoteCardInfo(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();

        //PROMISSORY NOTES
        sb.append("**Promissory Notes:**").append("\n");
        int index = 1;
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                    sb.append(Emojis.PN).append(Mapper.getPromissoryNote(pn.getKey(), true));
                    sb.append("\n");
                    index++;
                }
            }
            sb.append("\n");

            //PLAY AREA PROMISSORY NOTES
            sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    String pnData = Mapper.getPromissoryNote(pn.getKey(), true);
                    sb.append("`").append(index).append(".").append("(" + pn.getValue()).append(")`");
                    sb.append(Emojis.PN).append(pnData);
                    sb.append("\n");
                    index++;
                }
            }
        }
        return sb.toString();
    }   
}
