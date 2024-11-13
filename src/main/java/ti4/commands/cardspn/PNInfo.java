package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.InfoThreadCommand;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.Source.ComponentSource;

public class PNInfo extends PNCardsSubcommandData implements InfoThreadCommand {
    public PNInfo() {
        super(Constants.INFO, "Send your Promissory Notes to your Cards Info thread");
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendPromissoryNoteInfo(game, player, true, event);
        MessageHelper.sendMessageToEventChannel(event, "PN Info Sent");
    }

    @ButtonHandler("refreshPNInfo")
    public static void sendPromissoryNoteInfoLongForm(Game game, Player player) {
        sendPromissoryNoteInfo(game, player, true);
    }

    public static void sendPromissoryNoteInfo(Game game, Player player, boolean longFormat, GenericInteractionCreateEvent event) {
        checkAndAddPNs(game, player);
        game.checkPromissoryNotes();
        String headerText = player.getRepresentationUnfogged() + " Heads up, someone used some command";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendPromissoryNoteInfo(game, player, longFormat);
    }

    public static void sendPromissoryNoteInfo(Game game, Player player, boolean longFormat) {
        MessageHelper.sendMessageToChannelWithButtons(
            player.getCardsInfoThread(),
            getPromissoryNoteCardInfo(game, player, longFormat, false),
            getPNButtons(game, player));
    }

    private static List<Button> getPNButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String pnShortHand : player.getPromissoryNotes().keySet()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
            Player owner = game.getPNOwner(pnShortHand);
            if (owner == player || pnShortHand.endsWith("_ta"))
                continue;

            Button transact;
            if (game.isFowMode()) {
                transact = Buttons.green("resolvePNPlay_" + pnShortHand,
                    "Play " + owner.getColor() + " " + promissoryNote.getName());
            } else {
                transact = Buttons.green("resolvePNPlay_" + pnShortHand, "Play " + promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
            }
            buttons.add(transact);
        }
        return buttons;
    }

    public static String getPromissoryNoteCardInfo(Game game, Player player, boolean longFormat, boolean excludePlayArea) {
        StringBuilder sb = new StringBuilder();

        //PROMISSORY NOTES
        sb.append("**Promissory Notes:**").append("\n");
        int index = 1;
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            if (promissoryNotes.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                    if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                        sb.append("> `").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                        sb.append(getPromissoryNoteRepresentation(game, pn.getKey(), longFormat));
                        index++;
                    }
                }

                if (!excludePlayArea) {
                    //PLAY AREA PROMISSORY NOTES
                    sb.append("\n\n").append("__**PLAY AREA Promissory Notes:**__").append("\n");
                    if (promissoryNotesInPlayArea.isEmpty()) {
                        sb.append("> None");
                    } else {
                        for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                            if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                                sb.append("`").append(index).append(".");
                                sb.append("(").append(pn.getValue()).append(")`");
                                sb.append(getPromissoryNoteRepresentation(game, pn.getKey(), longFormat));
                                index++;
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String getPromissoryNoteRepresentation(Game game, String pnID) {
        return getPromissoryNoteRepresentation(game, pnID, null, true);
    }

    public static String getPromissoryNoteRepresentation(Game game, String pnID, boolean longFormat) {
        return getPromissoryNoteRepresentation(game, pnID, null, longFormat);
    }

    private static String getPromissoryNoteRepresentation(Game game, String pnID, Integer pnUniqueID, boolean longFormat) {
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
        Player pnOwner = game.getPNOwner(pnID);
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!game.isFowMode()) sb.append(pnOwner.getFactionEmoji());
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

    public static void checkAndAddPNs(Game game, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (!Mapper.isValidColor(playerColor) || !Mapper.isValidFaction(playerFaction)) {
            return;
        }

        // All PNs a Player brought to the game (owns)
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotesOwned());

        // Remove PNs in other players' hands and player areas and purged PNs
        for (Player player_ : game.getPlayers().values()) {
            promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
        }
        promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
        promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
        promissoryNotes.removeAll(game.getPurgedPN());

        // Any remaining PNs are missing from the game and can be re-added to the player's hand
        if (!promissoryNotes.isEmpty()) {
            for (String promissoryNote : promissoryNotes) {
                player.setPromissoryNote(promissoryNote);
            }
        }
    }
}
