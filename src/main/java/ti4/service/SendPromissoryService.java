package ti4.service;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class SendPromissoryService {

    public static void sendPromissoryToPlayer(
            GenericInteractionCreateEvent event, Game game, Player sender, Player receiver, String pnAlias) {
        sendPromissoryToPlayer(event, game, sender, receiver, pnAlias, false);
    }

    public static void sendPromissoryToPlayer(
            GenericInteractionCreateEvent event,
            Game game,
            Player sender,
            Player receiver,
            String pnAlias,
            boolean reportLost) {
        if (!valid(event, game, sender, receiver, pnAlias)) return;
        if (promissoryShouldBeReturnedFromPlayArea(game, sender, receiver, pnAlias)) {
            returnPromissoryFromPlayAreaToOwner(event, game, sender, receiver, pnAlias);
            return;
        }

        PromissoryNoteModel model = Mapper.getPromissoryNote(pnAlias);
        transferCardToReceiversHand(sender, receiver, pnAlias);
        addPromissoryNoteToPlayAreaIfAble(event, game, sender, receiver, model);

        // Report outcomes to relevant channels
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, sender, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, receiver, false);
        reportSentProm(game, sender, receiver, model, reportLost);
        Helper.checkEndGame(game, receiver);
    }

    private static boolean valid(
            GenericInteractionCreateEvent event, Game game, Player sender, Player receiver, String alias) {
        if (!sender.getPromissoryNotes().containsKey(alias)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error sending promissory note.");
            return false;
        }
        if (sender.getPromissoryNotesInPlayArea().contains(alias)) {
            Player owner = game.getPNOwner(alias);
            if (owner == null || owner == receiver) return true;
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "You cannot send promissory notes in your play area to anyone except the note's owner.");
            return false;
        }
        return true;
    }

    private static void transferCardToReceiversHand(Player sender, Player receiver, String id) {
        sender.removePromissoryNote(id);
        if (sender.getPromissoryNotesInPlayArea().contains(id)) sender.removePromissoryNoteFromPlayArea(id);
        receiver.setPromissoryNote(id);
    }

    private static void addPromissoryNoteToPlayAreaIfAble(
            GenericInteractionCreateEvent event, Game game, Player sender, Player receiver, PromissoryNoteModel model) {
        String id = model.getAlias();
        Player owner = game.getPNOwner(id);
        if (model.isPlayedDirectlyToPlayArea() && receiver != owner && !receiver.isPlayerMemberOfAlliance(owner)) {
            receiver.addPromissoryNoteToPlayArea(id);
            if (id.startsWith("dspnveld") && !receiver.getAllianceMembers().contains(owner.getFaction())) {
                PromissoryNoteHelper.resolvePNPlay(id, receiver, game, event);
            }
        }
    }

    private static void reportSentProm(
            Game game, Player sender, Player receiver, PromissoryNoteModel model, boolean reportLost) {
        String reportMsgFmt = sender.getRepresentation() + " sent %s of " + receiver.getRepresentation() + ".";
        String reportMsg;
        if (model.isPlayedDirectlyToPlayArea()) {
            reportMsg = String.format(reportMsgFmt, model.getNameRepresentation() + " directly to the play area");
        } else {
            reportMsg = String.format(reportMsgFmt, "a promissory note to the hand");
        }
        MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), reportMsg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(sender.getCorrectChannel(), reportMsg);
            String extra = null;
            if (model.getAlias().endsWith("_sftt")) extra = "Scores changed.";
            String whatSent = model.isPlayedDirectlyToPlayArea() ? model.getName() : "face down promissory note";
            FoWHelper.pingPlayersTransaction(game, null, sender, receiver, CardEmojis.PN + whatSent, extra);
        }
        if (reportLost) {
            MessageHelper.sendMessageToChannel(
                    sender.getCardsInfoThread(),
                    "# " + sender.getRepresentation() + " you lost the promissory note _" + model.getName() + "_.");
            MessageHelper.sendMessageToChannel(
                    receiver.getCardsInfoThread(),
                    "# " + receiver.getRepresentation() + " you gained the promissory note _" + model.getName() + "_.");
        }
    }

    public static void returnPromissoryFromPlayAreaToOwner(
            GenericInteractionCreateEvent event, Game game, Player sender, Player receiver, String pnAlias) {
        if (!promissoryShouldBeReturnedFromPlayArea(game, sender, receiver, pnAlias)) return;

        PromissoryNoteModel model = Mapper.getPromissoryNote(pnAlias);
        transferCardToReceiversHand(sender, receiver, pnAlias);

        // Report outcomes to relevant channels
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, sender, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, receiver, false);
        reportReturnedProm(game, sender, receiver, model);
    }

    private static boolean promissoryShouldBeReturnedFromPlayArea(
            Game game, Player sender, Player receiver, String alias) {
        return sender.getPromissoryNotesInPlayArea().contains(alias);
    }

    private static void reportReturnedProm(Game game, Player sender, Player receiver, PromissoryNoteModel model) {
        String reportMsg = sender.getRepresentation() + " returned " + model.getNameRepresentation() + " to "
                + receiver.getRepresentation() + ".";
        MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), reportMsg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(sender.getCorrectChannel(), reportMsg);
            String extra = null;
            if (model.getAlias().endsWith("_sftt")) extra = "Scores changed.";
            FoWHelper.pingPlayersTransaction(game, null, sender, receiver, CardEmojis.PN + model.getName(), extra);
        }
    }
}
