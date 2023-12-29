package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
import ti4.model.Source.ComponentSource;

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
        sendPromissoryNoteInfo(activeGame, player, true, event);
        sendMessage("PN Info Sent");
    }

    public static void sendPromissoryNoteInfo(Game activeGame, Player player, boolean longFormat, GenericInteractionCreateEvent event) {
        checkAndAddPNs(activeGame, player);
        activeGame.checkPromissoryNotes();
        String headerText = player.getRepresentation(true, true) + " Heads up, someone used some command";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendPromissoryNoteInfo(activeGame, player, longFormat);
    }

    public static void sendPromissoryNoteInfo(Game activeGame, Player player, boolean longFormat) {
        List<Button> buttons = new ArrayList<>();
        for (String pnShortHand : player.getPromissoryNotes().keySet()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
            Player owner = activeGame.getPNOwner(pnShortHand);
            if (owner == player)
                continue;

            Button transact;
            if (activeGame.isFoWMode()) {
                transact = Button.success("resolvePNPlay_" + pnShortHand,
                        "Play " + owner.getColor() + " " + promissoryNote.getName());
            } else {
                transact = Button.success("resolvePNPlay_" + pnShortHand, "Play " + promissoryNote.getName())
                        .withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
            }
            buttons.add(transact);
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), getPromissoryNoteCardInfo(activeGame, player, longFormat), buttons);
    }

    public static String getPromissoryNoteCardInfo(Game activeGame, Player player, boolean longFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");

        //PROMISSORY NOTES
        sb.append("__**Promissory Notes:**__").append("\n");
        int index = 1;
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
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
                sb.append("\n").append("__**PLAY AREA Promissory Notes:**__").append("\n");
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
        if (pnModel.getFaction().isPresent()) sb.append(Emojis.getFactionIconFromDiscord(pnModel.getFaction().get()));
        sb.append("__**").append(pnName).append("**__");
        sb.append(pnModel.getSource().emoji());
        sb.append("   ");

        String pnText = pnModel.getText();
        Player pnOwner = activeGame.getPNOwner(pnID);
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!activeGame.isFoWMode()) sb.append(pnOwner.getFactionEmoji());
            sb.append(Emojis.getColorEmojiWithName(pnOwner.getColor()));
            pnText = pnText.replaceAll(pnOwner.getColor(), Emojis.getColorEmojiWithName(pnOwner.getColor()));
        }

        if (longFormat || 
            Mapper.isValidFaction(pnModel.getFaction().orElse("").toLowerCase()) ||
            (pnModel.getSource() != ComponentSource.base && pnModel.getSource() != ComponentSource.pok)) {
                sb.append("      ").append(pnText);
            }
        sb.append("\n");
        return sb.toString();
    }

    public static void checkAndAddPNs(Game activeGame, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (!Mapper.isValidColor(playerColor) || !Mapper.isValidFaction(playerFaction)) {
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
