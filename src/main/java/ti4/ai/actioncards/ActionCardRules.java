package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.secrets.SpyNetwork;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.service.actioncard.SabotageService;
import ti4.service.agenda.IsPlayerElectedService;

@UtilityClass
public class ActionCardRules {

    private static final String REF_KEY = "acResolveRef|";
    private static final String FIELD = "~";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String SUMMIT_PRESET = "resolvePreassignment_Summit";
    private static final String SUMMIT_STORED = "Summit";
    private static final long CONFIRM_MILLIS = 60_000L;
    private static final long ANNOUNCEMENT_MILLIS = 5 * 60_000L;
    private static final long PROMPT_MILLIS = 2 * 60_000L;
    private static final long HOLD_MILLIS = 60_000L;
    private static final long SETTLE_MILLIS = 10_000L;
    private static final long OTHER_CANCEL_GRACE_MILLIS = 5 * 60_000L;
    private static final long WINDOW_CEILING_MILLIS = 24 * 60 * 60_000L;
    private static final Set<String> CENSURE = Set.of("censure", "absol_censure");

    public static Optional<AiDecision> playBeforePassing(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key) || !mayPlay(game, seat) || keepsSpyNetworkCards(seat)) return Optional.empty();
        Optional<ActionCardValue.Candidate> best = ActionCardValue.bestComponentPlay(game, seat);
        if (best.isEmpty()) return Optional.empty();
        return startPlay(context, key, best.get().alias());
    }

    public static Optional<AiDecision> holdOwnTurn(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        Optional<CardPlay> play =
                context.memory().get(PlayMemory.turnKey(context)).flatMap(CardPlay::decode);
        if (play.isEmpty() || !play.get().active()) return Optional.empty();
        String title = SabotageWindow.title(play.get().alias());
        return Optional.of(new AiDecision.Wait(context.now() + HOLD_MILLIS, "the " + title + " action card"));
    }

    public static Optional<AiDecision> continuePlay(AiTurnContext context) {
        recover(context);
        return advance(context, PlayMemory.turnKey(context)).or(() -> advance(context, PlayMemory.summitKey()));
    }

    private static void recover(AiTurnContext context) {
        String key = PlayMemory.turnKey(context);
        if (context.memory().has(key) || !context.isActivePlayer()) return;
        if (!"action".equalsIgnoreCase(context.game().getPhaseOfGame())) return;
        for (AiCard card : AiActionCards.inWindow(Window.COMPONENT_ACTION)) {
            Optional<Match> resolve =
                    CardPrompts.ownedPublic(context, Prompts.turnStart(context), card.resolveHandler()::equals);
            if (resolve.isEmpty()) continue;
            long playedAt = resolve.get().prompt().createdAtMillis();
            Optional<AiPrompt> announcement =
                    SabotageWindow.announcement(context, card.key(), playedAt - CONFIRM_MILLIS);
            CardPlay rebuilt = new CardPlay(
                    Stage.WINDOW,
                    context.now(),
                    playedAt - CONFIRM_MILLIS,
                    card.key(),
                    announcement.map(AiPrompt::messageId).orElse(""),
                    announcement.isPresent(),
                    lastPlayIndex(context.game(), context.seat(), card.key()),
                    "");
            PlayMemory.store(
                    context, key, announcement.isPresent() ? rebuilt : rebuilt.at(Stage.RESOLVE, context.now()));
            return;
        }
    }

    private static int lastPlayIndex(Game game, Player seat, String alias) {
        String title = SabotageWindow.title(alias);
        String playerId = GameStats.getTrackedPlayerId(seat);
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = plays.size() - 1; index >= 0; index--) {
            if (title.equals(plays.get(index).getActionCard())
                    && playerId != null
                    && playerId.equals(plays.get(index).getPlayerId())) {
                return index;
            }
        }
        return 0;
    }

    private static boolean censured(Game game, Player seat) {
        for (String law : CENSURE) {
            if (IsPlayerElectedService.isPlayerElected(game, seat, law)) return true;
        }
        return false;
    }

    private static long settleMillis(Game game, Player seat, CardPlay play) {
        if (play.windowExpected()) return SETTLE_MILLIS;
        boolean otherCancels = game.getRealPlayers().stream()
                .filter(player -> player != seat)
                .anyMatch(player -> SabotageService.couldUseInstinctTraining(player)
                        || SabotageService.couldUseWatcherMech(player, game));
        return otherCancels ? OTHER_CANCEL_GRACE_MILLIS : SETTLE_MILLIS;
    }

    public static Optional<AiDecision> presetSummit(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.getActionCards().containsKey(ActionCardValue.SUMMIT) || !mayPlay(game, seat)) return Optional.empty();
        String key = PlayMemory.summitKey();
        boolean tracked = context.memory()
                .get(key)
                .flatMap(CardPlay::decode)
                .filter(CardPlay::active)
                .isPresent();
        if (tracked) return Optional.empty();
        CardPlay preset =
                new CardPlay(Stage.PRESET, context.now(), context.now(), ActionCardValue.SUMMIT, "", false, 0, "");
        if (game.getStoredValue(SUMMIT_STORED).contains(seat.getFaction())) {
            PlayMemory.store(context, key, preset);
            return Optional.empty();
        }
        if (CommandTokenPolicy.gainWithinReinforcements(game, seat) < 1) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> candidate.isUnowned() && SUMMIT_PRESET.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                PlayMemory.store(context, key, preset);
                return Optional.of(AiDecision.press(prompt, button.get(), "pre-play Summit for 2 command tokens"));
            }
        }
        return Optional.empty();
    }

    static boolean mayPlay(Game game, Player seat) {
        if (censured(game, seat) || ButtonHelper.isPlayerOverLimit(game, seat)) return false;
        Player active = game.getActivePlayer();
        return !(seat.isPassed() && active != null && active.hasTech("tp"));
    }

    public static double frontlineValue(Game game, Player seat, String planetName) {
        return EconomyTargets.frontlineValue(game, seat, planetName);
    }

    private static boolean keepsSpyNetworkCards(Player seat) {
        return SpyNetwork.holds(seat) && seat.getAcCount() <= SpyNetwork.CARDS;
    }

    private static Optional<AiDecision> startPlay(AiTurnContext context, String key, String alias) {
        Integer index = context.seat().getActionCards().get(alias);
        if (index == null) return Optional.empty();
        String title = SabotageWindow.title(alias);
        Optional<Match> play = playButton(context, index, Long.MIN_VALUE);
        if (play.isPresent()) {
            PlayMemory.store(context, key, played(context, alias));
            return Optional.of(play.get().press("play " + title));
        }
        Optional<Match> menu = Prompts.owned(Prompts.thisTurn(context), context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        PlayMemory.store(context, key, new CardPlay(Stage.MENU, context.now(), context.now(), alias, "", false, 0, ""));
        return Optional.of(menu.get().press("open the component actions to play " + title));
    }

    private static CardPlay played(AiTurnContext context, String alias) {
        int plays = context.game().getGameStats().getActionCardPlays().size();
        boolean window = SabotageWindow.expected(context.game(), context.seat(), alias);
        return new CardPlay(Stage.PLAYED, context.now(), context.now(), alias, "", window, plays, "");
    }

    private static Optional<Match> playButton(AiTurnContext context, int index, long since) {
        String id = Constants.AC_PLAY_FROM_HAND + index;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            Optional<PromptButton> button =
                    prompt.firstEnabled(candidate -> candidate.isUnowned() && id.equals(candidate.handlerId()));
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(new Match(prompt, button.get()));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> advance(AiTurnContext context, String key) {
        Optional<CardPlay> stored = context.memory().get(key).flatMap(CardPlay::decode);
        if (stored.isEmpty() || !stored.get().active()) return Optional.empty();
        CardPlay play = stored.get();
        return switch (play.stage()) {
            case MENU -> fromMenu(context, key, play);
            case PRESET -> afterPreset(context, key, play);
            case PLAYED -> afterPlay(context, key, play);
            case WINDOW -> duringWindow(context, key, play);
            case RESOLVE -> resolve(context, key, play);
            case TARGET -> target(context, key, play);
            case DONE, CANCELED -> Optional.empty();
        };
    }

    private static Optional<AiDecision> move(AiTurnContext context, String key, CardPlay next) {
        PlayMemory.store(context, key, next);
        return next.active() ? advance(context, key) : Optional.empty();
    }

    private static Optional<AiDecision> stop(AiTurnContext context, String key, CardPlay play, Stage stage) {
        PlayMemory.moveTo(context, key, play, stage);
        return Optional.empty();
    }

    private static boolean expired(AiTurnContext context, CardPlay play, long limit) {
        return context.now() > play.since() + limit;
    }

    private static Optional<AiDecision> fromMenu(AiTurnContext context, String key, CardPlay play) {
        Integer index = context.seat().getActionCards().get(play.alias());
        if (index == null) return stop(context, key, play, Stage.DONE);
        Optional<Match> button = playButton(context, index, play.since() - CardPrompts.CLOCK_SKEW_MILLIS);
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        PlayMemory.store(context, key, played(context, play.alias()));
        return Optional.of(button.get().press("play " + SabotageWindow.title(play.alias())));
    }

    private static Optional<AiDecision> afterPreset(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) return Optional.empty();
        if (censured(context.game(), context.seat())) return stop(context, key, play, Stage.DONE);
        boolean window = SabotageWindow.expected(context.game(), context.seat(), play.alias());
        CardPlay played =
                new CardPlay(window ? Stage.WINDOW : Stage.RESOLVE, context.now(), 0, play.alias(), "", window, 0, "");
        return move(context, key, played);
    }

    private static Optional<AiDecision> afterPlay(AiTurnContext context, String key, CardPlay play) {
        if (context.seat().getActionCards().containsKey(play.alias())) {
            return expired(context, play, CONFIRM_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        return move(context, key, play.at(play.windowExpected() ? Stage.WINDOW : Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> duringWindow(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        CardPlay current = play;
        if (current.announcementId().isBlank()) {
            Optional<AiPrompt> announcement = SabotageWindow.announcement(context, play.alias(), play.playedAt());
            if (announcement.isEmpty()) {
                return expired(context, play, ANNOUNCEMENT_MILLIS)
                        ? stop(context, key, play, Stage.DONE)
                        : Optional.empty();
            }
            current = current.withAnnouncement(announcement.get().messageId());
            PlayMemory.store(context, key, current);
        }
        rememberResolveButton(context, key, current);
        boolean overdue = context.now() > current.since() + WINDOW_CEILING_MILLIS;
        if (!overdue && SabotageWindow.open(context, current.announcementId(), current.since()))
            return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, current, Stage.CANCELED);
        }
        return move(context, key, current.at(Stage.RESOLVE, context.now()));
    }

    private static Optional<AiDecision> resolve(AiTurnContext context, String key, CardPlay play) {
        Optional<AiCard> card = AiActionCards.of(play.alias());
        Game game = context.game();
        Player seat = context.seat();
        if (card.isEmpty() || card.get().resolveHandler() == null || censured(game, seat)) {
            return stop(context, key, play, Stage.DONE);
        }
        String handler = card.get().resolveHandler();
        if (context.now() < play.since() + settleMillis(game, seat, play)) return Optional.empty();
        if (SabotageWindow.canceled(game, seat, play.alias(), play.playsBefore())) {
            return stop(context, key, play, Stage.CANCELED);
        }
        Optional<Match> button = CardPrompts.ownedPublic(
                        context, play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS, handler::equals)
                .or(() -> rememberedResolveButton(context, key, handler));
        if (button.isEmpty()) {
            return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
        }
        context.memory().remove(REF_KEY + key);
        CardPlay next = play.at(card.get().hasTarget() ? Stage.TARGET : Stage.DONE, context.now())
                .withExtra("");
        PlayMemory.store(context, key, next);
        return Optional.of(button.get().press("resolve " + SabotageWindow.title(play.alias())));
    }

    private static void rememberResolveButton(AiTurnContext context, String key, CardPlay play) {
        String handler =
                AiActionCards.of(play.alias()).map(AiCard::resolveHandler).orElse(null);
        if (handler == null || context.memory().has(REF_KEY + key)) return;
        CardPrompts.ownedPublic(context, play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS, handler::equals)
                .ifPresent(match -> context.memory()
                        .put(
                                REF_KEY + key,
                                String.join(
                                        FIELD,
                                        match.prompt().channelId(),
                                        match.prompt().messageId(),
                                        match.button().customId(),
                                        match.button().label())));
    }

    private static Optional<Match> rememberedResolveButton(AiTurnContext context, String key, String handler) {
        String[] fields = StringUtils.splitPreserveAllTokens(
                context.memory().get(REF_KEY + key).orElse(""), FIELD);
        if (fields == null || fields.length != 4) return Optional.empty();
        PromptButton button = new PromptButton(0, fields[2], handler, context.faction(), fields[3], false);
        AiPrompt prompt =
                new AiPrompt(fields[0], fields[1], AiPrompt.PromptSource.PUBLIC, "", List.of(button), context.now());
        if (context.alreadyPressed(prompt, button)) return Optional.empty();
        return Optional.of(new Match(prompt, button));
    }

    private static Optional<AiDecision> target(AiTurnContext context, String key, CardPlay play) {
        Optional<AiDecision> choice = AiActionCards.of(play.alias())
                .filter(AiCard::hasTarget)
                .flatMap(card -> card.target().next(context, key, play));
        if (choice.isPresent()) return choice;
        return expired(context, play, PROMPT_MILLIS) ? stop(context, key, play, Stage.DONE) : Optional.empty();
    }
}
