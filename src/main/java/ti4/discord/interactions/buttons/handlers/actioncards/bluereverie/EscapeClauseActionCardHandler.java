package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class EscapeClauseActionCardHandler {

    private static final String PRESET = "escapeClausePreset";
    private static final String PENDING = "escapeClausePending";

    public static void offerPreset(Game game, Player player) {
        if (!player.getPlayableActionCards().contains("escape_clause")
                || !game.getStoredValue(PRESET).isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may preset _Escape Clause_ to play when another player resolves your _Trade Agreement_.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + "presetEscapeClause", "Preset Escape Clause"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler("presetEscapeClause")
    public static void presetEscapeClause(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.getPlayableActionCards().contains("escape_clause")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(PRESET, player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + " preset _Escape Clause_.",
                List.of(Buttons.red(player.factionButtonChecker() + "removeEscapeClausePreset", "Remove Preset")));
    }

    @ButtonHandler("removeEscapeClausePreset")
    public static void removeEscapeClausePreset(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getFaction().equals(game.getStoredValue(PRESET))) game.removeStoredValue(PRESET);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean triggerPreset(
            GenericInteractionCreateEvent event,
            Game game,
            Player tradeAgreementHolder,
            Player owner,
            String promissoryNoteId) {
        if (!owner.getFaction().equals(game.getStoredValue(PRESET))
                || !owner.getPlayableActionCards().contains("escape_clause")) return false;
        game.setStoredValue(
                PENDING, owner.getFaction() + "|" + tradeAgreementHolder.getFaction() + "|" + promissoryNoteId);
        game.removeStoredValue(PRESET);
        String error = ActionCardHelper.playAC(event, game, owner, "escape_clause", game.getMainGameChannel());
        if (error == null) return true;
        game.removeStoredValue(PENDING);
        MessageHelper.sendMessageToChannel(owner.getCardsInfoThread(), error);
        return false;
    }

    public static void offerResolution(Game game, Player player, String introMessage) {
        if (getPendingOwner(game) != player) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                introMessage,
                List.of(Buttons.green(player.factionButtonChecker() + "resolveEscapeClause", "Resolve Escape Clause")));
    }

    @ButtonHandler("resolveEscapeClause")
    public static void resolveEscapeClause(ButtonInteractionEvent event, Game game, Player player) {
        String[] pending = game.getStoredValue(PENDING).split("\\|", -1);
        if (pending.length != 3 || !player.getFaction().equals(pending[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player tradeAgreementHolder = game.getPlayerFromColorOrFaction(pending[1]);
        if (tradeAgreementHolder == null
                || !tradeAgreementHolder.getPromissoryNotes().containsKey(pending[2])) {
            game.removeStoredValue(PENDING);
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(PENDING);
        tradeAgreementHolder.removePromissoryNote(pending[2]);
        player.setPromissoryNote(pending[2]);
        String tradeGoodChange = tradeAgreementHolder.gainTG(2, true);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, tradeAgreementHolder, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        MessageHelper.sendMessageToChannel(
                tradeAgreementHolder.getCorrectChannel(),
                tradeAgreementHolder.getRepresentation() + " returned a _Trade Agreement_ and gained 2 trade goods "
                        + tradeGoodChange + " from _Escape Clause_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSabotagedEscapeClause(
            GenericInteractionCreateEvent event, Game game, Player actionCardPlayer, String actionCardName) {
        if (!"Escape Clause".equals(actionCardName) || getPendingOwner(game) != actionCardPlayer) return;
        String[] pending = game.getStoredValue(PENDING).split("\\|", -1);
        game.removeStoredValue(PENDING);
        if (pending.length != 3) return;
        Player tradeAgreementHolder = game.getPlayerFromColorOrFaction(pending[1]);
        if (tradeAgreementHolder != null)
            PromissoryNoteHelper.resolvePNPlay(pending[2], tradeAgreementHolder, game, event);
    }

    private static Player getPendingOwner(Game game) {
        String[] pending = game.getStoredValue(PENDING).split("\\|", -1);
        return pending.length == 3 ? game.getPlayerFromColorOrFaction(pending[0]) : null;
    }
}
