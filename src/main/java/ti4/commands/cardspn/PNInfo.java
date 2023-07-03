package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

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
        checkAndAddPNs(activeMap, player);
        activeMap.checkPromissoryNotes();
        sendPromissoryNoteInfo(activeMap, player, true, event);
        sendMessage("PN Info Sent");
    }

    public static void sendPromissoryNoteInfo(Map activeMap, Player player, boolean longFormat, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeMap,activeMap.getGuild(), true) + " Heads up, someone used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
        sendPromissoryNoteInfo(activeMap, player, longFormat);
    }

    public static void sendPromissoryNoteInfo(Map activeMap, Player player, boolean longFormat) {
        //PN INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getPromissoryNoteCardInfo(activeMap, player, longFormat));

        //BUTTONS
        String pnPlayMessage = "_ _\nClick a button below to play a Promissory Note";
        List<Button> pnButtons = getPlayablePNButtons(activeMap, player);
        if (pnButtons != null && !pnButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(pnPlayMessage, pnButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    }

    private static List<Button> getPlayablePNButtons(Map activeMap, Player player) {
        List<Button> pnButtons = new ArrayList<>();
        //TODO: PN BUTTONS
        return pnButtons;
    }

    public static String getPromissoryNoteCardInfo(Map activeMap, Player player, boolean longFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        //PROMISSORY NOTES
        sb.append("**Promissory Notes:**").append("\n");
        int index = 1;
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            if (promissoryNotes.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                    if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                        sb.append("`").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                        sb.append(getPromissoryNoteRepresentation(activeMap, pn.getKey(), longFormat));
                        index++;
                    }
                }
                sb.append("\n");

                //PLAY AREA PROMISSORY NOTES
                sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
                if (promissoryNotesInPlayArea.isEmpty()) {
                    sb.append("> None");
                } else {
                    for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                        if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                            sb.append("`").append(index).append(".");
                            sb.append("(" + pn.getValue()).append(")`");
                            sb.append(getPromissoryNoteRepresentation(activeMap, pn.getKey(), longFormat));
                            index++;
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String getPromissoryNoteRepresentationShort(Map activeMap, String pnID) {
        return getPromissoryNoteRepresentation(activeMap, pnID, null, false);
    }

    public static String getPromissoryNoteRepresentation(Map activeMap, String pnID) {
        return getPromissoryNoteRepresentation(activeMap, pnID, null, true);
    }

    public static String getPromissoryNoteRepresentation(Map activeMap, String pnID, boolean longFormat) {
        return getPromissoryNoteRepresentation(activeMap, pnID, null, longFormat);
    }

    private static String getPromissoryNoteRepresentation(Map activeMap, String pnID, Integer pnUniqueID, boolean longFormat) {
        PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pnID);
        if (pnModel == null) {
            String error = "Could not find representation for PN ID: " + pnID;
            BotLogger.log(error);
            return error;
        }
        String pnName = pnModel.getName();
        StringBuilder sb = new StringBuilder();

        sb.append(Emojis.PN);
        if (pnModel.getFaction() != null && !pnModel.getFaction().isEmpty()) sb.append(Helper.getFactionIconFromDiscord(pnModel.getFaction()));
        sb.append("__**" + pnName + "**__   ");

        String pnText = pnModel.getText();
        Player pnOwner = activeMap.getPNOwner(pnID);
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!activeMap.isFoWMode()) sb.append(Helper.getFactionIconFromDiscord(pnOwner.getFaction()));
            sb.append(Helper.getRoleMentionByName(activeMap.getGuild(), pnOwner.getColor()));
            // if (!activeMap.isFoWMode()) sb.append("(").append(pnOwner.getUserName()).append(")");
            pnText = pnText.replaceAll(pnOwner.getColor(), Helper.getRoleMentionByName(activeMap.getGuild(), pnOwner.getColor()));
        }
        
        if (longFormat || Mapper.isFaction(pnModel.getFaction().toLowerCase()) || pnModel.getSource().equalsIgnoreCase("Absol")) sb.append("      ").append(pnText);
        sb.append("\n");
        return sb.toString();
    }

    public static void checkAndAddPNs(Map activeMap, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (!Mapper.isColorValid(playerColor) || !Mapper.isFaction(playerFaction)) {
            return;
        }
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotesOwned());
        for (Player player_ : activeMap.getPlayers().values()) {
            promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
        }
        promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
        promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
        promissoryNotes.removeAll(activeMap.getPurgedPN());
        
        if (!promissoryNotes.isEmpty()) {
            for (String promissoryNote : promissoryNotes) {
                player.setPromissoryNote(promissoryNote);
            }
        }
    }
}
