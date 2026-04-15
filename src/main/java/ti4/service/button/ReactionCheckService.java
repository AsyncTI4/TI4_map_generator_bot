package ti4.service.button;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.service.StatusCleanupService;
import ti4.service.game.StartPhaseService;

@UtilityClass
public class ReactionCheckService {

    public static void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        mainMessage.clearReactions().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void checkForAllReactions(@Nullable ButtonInteractionEvent event, Game game) {
        if (event == null) {
            return;
        }
        String buttonID = event.getButton().getCustomId();

        String messageId = event.getInteraction().getMessage().getId();
        var gameMessage = GameMessageManager.getOne(game.getName(), messageId);
        int matchingFactionReactions = 0;
        if (buttonID != null
                && (buttonID.contains("po_scoring")
                        || buttonID.contains("po_no_scoring")
                        || buttonID.contains("so_no_scoring")
                        || buttonID.contains("so_score_hand"))) {
            boolean allReacted = true;
            for (Player player : game.getRealPlayers()) {
                String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
                String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                if (po.isEmpty()
                        || so.isEmpty()
                        || game.getPhaseOfGame().contains("action")
                        || game.getPhaseOfGame().contains("agenda")) {
                    allReacted = false;
                }
            }
            if (allReacted) {
                respondAllPlayersReacted(event, game);
                GameMessageManager.remove(game.getName(), messageId);
            }
        } else {

            for (Player player : game.getRealPlayers()) {
                boolean factionReacted = false;
                String faction = player.getFaction();
                if (gameMessage.isPresent()
                        && gameMessage.get().factionsThatReacted().contains(faction)) {
                    factionReacted = true;
                } else if (buttonID.contains("no_after")) {
                    if (game.getPlayersWhoHitPersistentNoAfter().contains(faction)) {
                        factionReacted = true;
                    } else {
                        Message mainMessage = event.getMessage();
                        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                        MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                        if (reaction != null) {
                            factionReacted = true;
                        }
                    }
                } else if (buttonID.contains("no_when")) {
                    if (game.getPlayersWhoHitPersistentNoWhen().contains(faction)) {
                        factionReacted = true;
                    } else {
                        Message mainMessage = event.getMessage();
                        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                        MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                        if (reaction != null) {
                            factionReacted = true;
                        }
                    }
                }
                if (factionReacted) {
                    matchingFactionReactions++;
                }
            }
            int numberOfPlayers = game.getRealPlayers().size();
            if (matchingFactionReactions >= numberOfPlayers) {
                respondAllPlayersReacted(event, game);
                GameMessageManager.remove(game.getName(), messageId);
            }
        }
    }

    public static void respondAllHaveScored(Game game) {
        String message2 =
                "All players have indicated scoring. Flip the relevant public objective using the buttons. This will automatically run status clean-up if it has not been run already.";
        Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
        Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
        Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
        List<Button> buttons = new ArrayList<>();
        if (game.isRedTapeMode() || game.isCivilizedSocietyMode()) {
            message2 = "All players have indicated scoring. In this game mode, no objective is revealed at this stage."
                    + " Please press one of the buttons below anyways though - don't worry, it won't reveal anything, it will just run cleanup.";
        }
        if (game.getRound() < 4 || !game.getPublicObjectives1Peekable().isEmpty()) {
            buttons.add(drawStage1);
        }
        if ((game.getRound() > 3 || game.getPublicObjectives1Peekable().isEmpty()) && !game.isOmegaPhaseMode()) {
            if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                buttons.add(draw2Stage2);
            } else {
                buttons.add(drawStage2);
            }
        }
        var endGameDeck =
                game.isOmegaPhaseMode() ? game.getPublicObjectives1Peekable() : game.getPublicObjectives2Peekable();
        var endGameRound = game.isOmegaPhaseMode() ? 9 : 7;
        if ((game.getRound() > endGameRound || endGameDeck.isEmpty())
                && !game.isRedTapeMode()
                && !game.isCivilizedSocietyMode()) {
            if (game.isFowMode()) {
                message2 += "\n> - If there are no more objectives to reveal, use the button to continue as is.";
                message2 += " Or end the game manually.";
                buttons.add(Buttons.green("reveal_stage_none", "Continue without revealing"));
            } else {
                message2 += "\n> - If there are no more objectives to reveal, use the button to end the game.";
                message2 +=
                        " Whoever has the most points is crowned the winner, or whoever has the earliest initiative in the case of ties.";

                buttons.add(Buttons.red("gameEnd", "End Game"));
                buttons.add(Buttons.blue("rematch", "Rematch (make new game with same players/channels)"));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message2, buttons);
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getCustomId();
        if (game == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)
                || buttonID.contains(Constants.PO_NO_SCORING)
                || buttonID.contains("so_no_scoring")
                || buttonID.contains("so_score_hand")) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction()
                    .getMessage()
                    .reply("All players have reacted to \"" + buttonText + "\".")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW,
                    "sc_refresh",
                    "sc_refresh_and_wash",
                    "trade_primary",
                    "sc_ac_draw",
                    "sc_draw_so",
                    "sc_trade_follow" -> {
                String message = "All players have reacted to this strategy card.";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" ->
                ReactionService.handleAllPlayersReactingNoWhens(
                        event.getInteraction().getMessage(), game);
            case "no_after", "no_after_persistent" ->
                ReactionService.handleAllPlayersReactingNoAfters(
                        event.getInteraction().getMessage(), game);
            case "no_sabotage" ->
                ReactionService.handleAllPlayersReactingNoSabotage(
                        event.getInteraction().getMessage(), game);
            case Constants.PO_SCORING -> respondAllHaveScored(game);
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please proceed to the Edict Phase now.", buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please flip agenda now.", buttons);
                    }
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            game.getPing()
                                    + ", all players have indicated completion of Status Phase. Proceeding to Strategy Phase.");
                    StartPhaseService.startPhase(event, game, "strategy");
                }
                if (game.isFowMode()) {
                    game.setStoredValue("fowStatusDone", "");
                    StatusCleanupService.returnEndStatusPNs(game);
                }
            }
            case "redistributeCCButtons" -> {
                StatusCleanupService.returnEndStatusPNs(
                        game); // return any PNs with "end of status phase" return timing
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "Please proceed to Edict Phase after the last person finishing doing gaining and redistributing command tokens.",
                                buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                        + " Please press the \"Flip Agenda\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                                buttons);
                    }

                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    String condition;
                    if (game.isOrdinianC1Mode()) {
                        condition = "the _Coatl_ is still damaged";
                    } else if (game.isTwilightsFallMode()) {
                        condition = "there is no Tyrant";
                    } else {
                        condition = "the Custodians token is still on Mecatol Rex";
                    }
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(),
                            "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                    + " As " + condition + ", there will be no Agenda Phase this round."
                                    + " Please press the \"Start Strategy Phase\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                            buttons);
                }
            }
        }
    }
}
