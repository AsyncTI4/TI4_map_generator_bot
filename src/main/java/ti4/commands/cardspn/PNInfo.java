package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Map;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        checkAndAddPNs(activeGame, player);
        activeGame.checkPromissoryNotes();
        sendPromissoryNoteInfo(activeGame, player, true, event);
        sendMessage("PN Info Sent");
    }

    public static void sendPromissoryNoteInfo(Game activeGame, Player player, boolean longFormat, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Heads up, someone used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendPromissoryNoteInfo(activeGame, player, longFormat);
    }

    public static void sendPromissoryNoteInfo(Game activeGame, Player player, boolean longFormat) {
        //PN INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getPromissoryNoteCardInfo(activeGame, player, longFormat));

        //BUTTONS
        List<Button> buttons = new ArrayList<>();
        for(String pnShortHand : player.getPromissoryNotes().keySet())
        {
            if(player.getPromissoryNotesInPlayArea().contains(pnShortHand)){
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNoteByID(pnShortHand);
            Player owner = activeGame.getPNOwner(pnShortHand);
            if(owner == player){
            }else{
                Button transact;
                if(activeGame.isFoWMode()){
                    transact = Button.success("resolvePNPlay_"  + pnShortHand, "Play " +owner.getColor() +" "+ promissoryNote.getName());
                }else{
                    transact = Button.success("resolvePNPlay_" + pnShortHand, "Play " + promissoryNote.getName()).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(owner.getFaction())));
                }
                buttons.add(transact);
            }
        }
        Button transaction = Button.primary("transaction", "Transaction");
        buttons.add(transaction);
        Button modify = Button.secondary("getModifyTiles", "Modify Units");
        buttons.add(modify);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), "You can use these buttons to play a PN, resolve a transaction, or to modify units", buttons);
    }

   

    public static String getPromissoryNoteCardInfo(Game activeGame, Player player, boolean longFormat) {
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
                for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                    if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                        sb.append("`").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                        sb.append(getPromissoryNoteRepresentation(activeGame, pn.getKey(), longFormat));
                        index++;
                    }
                }
                sb.append("\n");

                //PLAY AREA PROMISSORY NOTES
                sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
                if (promissoryNotesInPlayArea.isEmpty()) {
                    sb.append("> None");
                } else {
                    for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                        if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                            sb.append("`").append(index).append(".");
                            sb.append("(").append(pn.getValue()).append(")`");
                            sb.append(getPromissoryNoteRepresentation(activeGame, pn.getKey(), longFormat));
                            index++;
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String getPromissoryNoteRepresentationShort(Game activeGame, String pnID) {
        return getPromissoryNoteRepresentation(activeGame, pnID, null, false);
    }

    public static String getPromissoryNoteRepresentation(Game activeGame, String pnID) {
        return getPromissoryNoteRepresentation(activeGame, pnID, null, true);
    }

    public static String getPromissoryNoteRepresentation(Game activeGame, String pnID, boolean longFormat) {
        return getPromissoryNoteRepresentation(activeGame, pnID, null, longFormat);
    }

    private static String getPromissoryNoteRepresentation(Game activeGame, String pnID, Integer pnUniqueID, boolean longFormat) {
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
        sb.append("__**").append(pnName).append("**__   ");

        String pnText = pnModel.getText();
        Player pnOwner = activeGame.getPNOwner(pnID);
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!activeGame.isFoWMode()) sb.append(Helper.getFactionIconFromDiscord(pnOwner.getFaction()));
            sb.append(Helper.getRoleMentionByName(activeGame.getGuild(), pnOwner.getColor()));
            // if (!activeMap.isFoWMode()) sb.append("(").append(pnOwner.getUserName()).append(")");
            pnText = pnText.replaceAll(pnOwner.getColor(), Helper.getRoleMentionByName(activeGame.getGuild(), pnOwner.getColor()));
        }
        
        if (longFormat || Mapper.isFaction(pnModel.getFaction().toLowerCase()) || "Absol".equalsIgnoreCase(pnModel.getSource())) sb.append("      ").append(pnText);
        sb.append("\n");
        return sb.toString();
    }

    public static void checkAndAddPNs(Game activeGame, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (!Mapper.isColorValid(playerColor) || !Mapper.isFaction(playerFaction)) {
            return;
        }

        // All PNs a Player brought to the game (owns)
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotesOwned());

        // Remove PNs in other players' hands and player areas and purged PNs
        for (Player player_ : activeGame.getPlayers().values()) {
            promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
        }
        promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
        promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
        promissoryNotes.removeAll(activeGame.getPurgedPN());
        
        // Any remaining PNs are missing from the game and can be re-added to the player's hand
        if (!promissoryNotes.isEmpty()) {
            for (String promissoryNote : promissoryNotes) {
                player.setPromissoryNote(promissoryNote);
            }
        }
    }
}
