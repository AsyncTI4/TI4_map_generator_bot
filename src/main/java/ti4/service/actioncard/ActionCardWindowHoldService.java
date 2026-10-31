package ti4.service.actioncard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.button.ReactionService;
import ti4.service.game.StartPhaseService;

@UtilityClass
public class ActionCardWindowHoldService {

    public static final String PASS_BUTTON_ID = "acWindowPass";
    public static final String BYPASS_BUTTON_ID = "acWindowBypass";

    @Getter
    @RequiredArgsConstructor
    public enum HeldWindow {
        CORRUPTION(List.of("corruption"), "Status cleanup, including drawing action cards,"),
        STRATEGY_PHASE_START(List.of("deflection", "revolution"), "The first strategy card pick");

        private final List<String> cardIds;
        private final String heldStep;
    }

    public static boolean openCorruptionHold(Game game) {
        return open(game, HeldWindow.CORRUPTION);
    }

    public static boolean openStrategyPhaseHold(Game game) {
        return game.getRound() > 1 && open(game, HeldWindow.STRATEGY_PHASE_START);
    }

    public static List<String> liveCardIds(Game game, HeldWindow window) {
        return window.getCardIds().stream()
                .filter(cardId -> !game.isACInDiscard(cardName(cardId)))
                .toList();
    }

    public static boolean couldPlay(Player player, HeldWindow window) {
        return ActionCardHelper.canPlayActionCards(player)
                && player.getPlayableActionCards().stream().anyMatch(window.getCardIds()::contains);
    }

    public static Optional<HeldWindow> windowOf(GameMessage gameMessage) {
        if (gameMessage.type() != GameMessageType.ACTION_CARD_WINDOW || gameMessage.key() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(HeldWindow.valueOf(gameMessage.key()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean open(Game game, HeldWindow window) {
        if (!game.isAcd2()) {
            return false;
        }
        List<String> liveCardIds = liveCardIds(game, window);
        if (liveCardIds.isEmpty()) {
            return false;
        }
        List<Player> playersWhoMustAnswer = game.getRealPlayers().stream()
                .filter(ActionCardHelper::canPlayActionCards)
                .toList();
        if (playersWhoMustAnswer.isEmpty()) {
            return false;
        }

        String cards = italicCardNames(liveCardIds);
        String message = "### " + game.getPing() + ", this is the window for " + cards + ".\n"
                + window.getHeldStep() + " will wait until every player has played or passed on " + cards
                + ". If you play one, press the button once it has resolved.";
        String buttonLabel = "No "
                + String.join(
                        " or ",
                        liveCardIds.stream()
                                .map(ActionCardWindowHoldService::cardName)
                                .toList());
        MessageHelper.splitAndSentWithAction(
                message,
                game.getMainGameChannel(),
                List.of(
                        Buttons.blue(PASS_BUTTON_ID, buttonLabel),
                        Buttons.gray(BYPASS_BUTTON_ID, "Don't Wait, Move On")),
                sent -> registerHold(game, window, sent, playersWhoMustAnswer));
        remindHolders(game, liveCardIds);
        return true;
    }

    private static void registerHold(Game game, HeldWindow window, Message sent, List<Player> playersWhoMustAnswer) {
        LinkedHashSet<String> alreadyAnswered = new LinkedHashSet<>();
        for (Player player : game.getRealPlayers()) {
            if (playersWhoMustAnswer.contains(player)) {
                continue;
            }
            alreadyAnswered.add(player.getFaction());
            sent.addReaction(Helper.getPlayerReactionEmoji(game, player, sent))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        GameMessageManager.add(
                game.getName(),
                new GameMessage(
                        sent.getId(),
                        GameMessageType.ACTION_CARD_WINDOW,
                        alreadyAnswered,
                        game.getLastModifiedDate(),
                        window.name()));
    }

    private static void remindHolders(Game game, List<String> liveCardIds) {
        for (Player player : game.getRealPlayers()) {
            if (!ActionCardHelper.canPlayActionCards(player)) {
                continue;
            }
            for (String cardId : liveCardIds) {
                if (player.getPlayableActionCards().contains(cardId)) {
                    MessageHelper.sendMessageToChannel(
                            player.getCardsInfoThread(),
                            player.getRepresentationUnfogged() + ", a reminder this is the window to play _"
                                    + cardName(cardId) + "_.");
                }
            }
        }
    }

    public static void onActionCardPlayed(Game game, Player cardPlayer, String acId) {
        for (GameMessage hold : GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD_WINDOW)) {
            HeldWindow window = windowOf(hold).orElse(null);
            if (window == null
                    || !window.getCardIds().contains(acId)
                    || !liveCardIds(game, window).isEmpty()) {
                continue;
            }
            for (Player player : game.getRealPlayers()) {
                if (player == cardPlayer || hold.factionsThatReacted().contains(player.getFaction())) {
                    continue;
                }
                ReactionService.addReaction(player, false, null, null, hold.messageId(), game);
            }
        }
    }

    public static void close(Game game, GameMessage hold, @Nullable GenericInteractionCreateEvent event) {
        release(game, hold, event, "All players have played or passed. Moving on.");
    }

    public static void bypass(Game game, Player player, ButtonInteractionEvent event) {
        GameMessage hold = GameMessageManager.getOne(game.getName(), event.getMessageId())
                .filter(message -> message.type() == GameMessageType.ACTION_CARD_WINDOW)
                .orElse(null);
        if (hold == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This window has already closed.");
            return;
        }
        release(
                game,
                hold,
                event,
                FoWHelper.actorOrAnon(game, player, "A player") + " chose not to wait for this window. Moving on.");
    }

    private static void release(
            Game game, GameMessage hold, @Nullable GenericInteractionCreateEvent event, String announcement) {
        HeldWindow window = windowOf(hold).orElse(null);
        GameMessageManager.remove(game.getName(), hold.messageId());
        if (window == null) {
            return;
        }
        game.getMainGameChannel()
                .retrieveMessageById(hold.messageId())
                .queue(
                        message -> {
                            message.editMessageComponents().queue(Consumers.nop(), BotLogger::catchRestError);
                            message.reply(announcement).queueAfter(100, TimeUnit.MILLISECONDS);
                        },
                        BotLogger::catchRestError);
        switch (window) {
            case CORRUPTION -> StartPhaseService.startStatusHomework(event, game);
            case STRATEGY_PHASE_START -> {
                Player firstPicker = game.getActivePlayer();
                if (firstPicker != null) {
                    StartPhaseService.promptFirstStrategyCardPick(game, firstPicker);
                }
            }
        }
    }

    private static String italicCardNames(List<String> cardIds) {
        StringJoiner names = new StringJoiner(" or ");
        for (String cardId : cardIds) {
            names.add("_" + cardName(cardId) + "_");
        }
        return names.toString();
    }

    private static String cardName(String cardId) {
        return Mapper.getActionCard(cardId).getName();
    }
}
