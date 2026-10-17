package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.agenda.AgendaClock;
import ti4.ai.agenda.AgendaForecast;
import ti4.ai.agenda.AgendaPolicy;
import ti4.ai.agenda.AgendaQueue;
import ti4.ai.agenda.AgendaVoting;
import ti4.ai.agenda.RiderPayoff;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;

@UtilityClass
public class AgendaCardRules {

    static final List<String> RIDER_TITLES = List.of(
            RiderPayoff.IMPERIAL,
            RiderPayoff.TRADE,
            RiderPayoff.POLITICS,
            RiderPayoff.TECHNOLOGY,
            RiderPayoff.LEADERSHIP);
    private static final String RIDER_KEY = "agendaRider|";
    private static final String PLAYED_KEY = "agendaRiderPlayed|";
    private static final String FIELD = "~";
    private static final String WAITING_PHASE = "agendawaiting";
    private static final String OPEN_AFTERS = "queueAnAfter";
    private static final String OPEN_WHENS = "queueAWhen";
    private static final String QUEUED_WHENS = "queuedWhens";
    private static final String DECLINED_WHENS = "declinedWhens";
    private static final String QUEUED_WHEN_OF = "queuedWhensFor";
    private static final String VETO_SEEN_KEY = "agendaVetoPlayed|";
    private static final String QUEUE_WHEN_PREFIX = "queueWhen_ac_";
    private static final String WHENS_RESOLVED = "whensResolved";
    private static final String QUEUE_PREFIX = "queueAfter_ac_";
    private static final String QUEUED_CARD = "ac_";
    private static final String LOCK = "lockAftersIn";
    private static final String DECLINE = "declineToQueueAnAfter";
    private static final String RIDER = "rider_";
    private static final String PLANET_RIDER = "planetRider_";
    private static final String SEPARATOR = "_";
    private static final long GIVE_UP_MILLIS = 5 * 60_000L;

    record Choice(String alias, String title, String outcome, double gain) {}

    private record Entry(String alias, int playsBefore) {

        String encode() {
            return alias + FIELD + playsBefore;
        }

        static Optional<Entry> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 2 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            return Optional.of(new Entry(fields[0], Integer.parseInt(fields[1])));
        }

        String title() {
            return SabotageWindow.title(alias);
        }
    }

    public static Optional<AiDecision> riders(AiTurnContext context) {
        Game game = context.game();
        if (!game.getPhaseOfGame().toLowerCase().startsWith("agenda")) return Optional.empty();
        AgendaClock.seenAt(context);
        return abandonQueuedRider(context).or(() -> lockQueuedRider(context)).or(() -> placeRider(context));
    }

    public static boolean holdsUnplacedRider(AiTurnContext context) {
        Optional<Entry> entry = entry(context);
        if (entry.isEmpty()) return false;
        Game game = context.game();
        Player seat = context.seat();
        String title = entry.get().title();
        if (placed(game, seat, title) || canceled(game, seat, title, entry.get().playsBefore())) return false;
        if (!playedSince(game, seat, title, entry.get().playsBefore())) return false;
        return context.now() - playedSeenAt(context) < GIVE_UP_MILLIS;
    }

    public static boolean holdsPlayedVeto(AiTurnContext context) {
        if (!vetoPlayedAndPending(context.game(), context.seat())) return false;
        Optional<CardPlay> play =
                context.memory().get(PlayMemory.agendaKey(context)).flatMap(CardPlay::decode);
        if (play.isPresent()) return play.get().active();
        return context.now() - vetoSeenAt(context) < GIVE_UP_MILLIS;
    }

    public static boolean vetoPlayedAndPending(Game game, Player seat) {
        boolean whensOpen = WAITING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                && game.getStoredValue(WHENS_RESOLVED).isEmpty();
        boolean consumed = !game.getStoredValue(QUEUED_WHENS).contains(seat.getFaction() + SEPARATOR)
                && !game.getStoredValue(DECLINED_WHENS).contains(seat.getFaction() + SEPARATOR);
        boolean vetoSlot = Optional.of(game.getStoredValue(QUEUED_WHEN_OF + seat.getFaction()))
                .filter(queued -> queued.startsWith(QUEUED_CARD))
                .map(queued -> StringUtils.removeStart(queued, QUEUED_CARD))
                .filter(alias -> AgendaCards.VETO_TITLE.equals(SabotageWindow.title(alias)))
                .isPresent();
        return whensOpen && consumed && vetoSlot && !SabotageWindow.canceledPlay(game, seat, AgendaCards.VETO_TITLE, 0);
    }

    private static long vetoSeenAt(AiTurnContext context) {
        String key = VETO_SEEN_KEY + context.game().getCurrentAgendaInfo();
        Optional<String> seen = context.memory().get(key).filter(StringUtils::isNumeric);
        if (seen.isPresent()) return Long.parseLong(seen.get());
        context.memory().put(key, String.valueOf(context.now()));
        return context.now();
    }

    public static Optional<AiDecision> queueVeto(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        boolean whensOpen = WAITING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                && game.getStoredValue(WHENS_RESOLVED).isEmpty();
        if (!whensOpen || AgendaQueue.holdsQueuedWhen(game, seat) || !ActionCardRules.mayPlayNow(game, seat)) {
            return Optional.empty();
        }
        Optional<String> veto = seat.getActionCards().keySet().stream()
                .sorted()
                .filter(alias -> AgendaCards.VETO_TITLE.equals(SabotageWindow.title(alias)))
                .findFirst();
        if (veto.isEmpty() || !AgendaPolicy.worthDiscarding(game, seat)) return Optional.empty();
        return pressHidden(context, QUEUE_WHEN_PREFIX + veto.get(), "queue Veto")
                .or(() -> pressHidden(context, OPEN_WHENS, "look at its when options to play Veto"));
    }

    public static Optional<AiDecision> queueRider(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        boolean waiting = WAITING_PHASE.equalsIgnoreCase(game.getPhaseOfGame());
        if (!waiting || entry(context).isPresent() || AgendaQueue.holdsQueuedAfter(game, seat)) {
            return Optional.empty();
        }
        if (!ActionCardRules.mayPlayNow(game, seat)) return Optional.empty();
        Optional<Choice> best = bestRider(context);
        if (best.isEmpty()) return Optional.empty();
        Optional<AiDecision> queue = pressHidden(
                context,
                QUEUE_PREFIX + best.get().alias(),
                "queue " + best.get().title());
        if (queue.isPresent()) {
            int plays = game.getGameStats().getActionCardPlays().size();
            context.memory().put(entryKey(context), new Entry(best.get().alias(), plays).encode());
            return queue;
        }
        return pressHidden(
                context,
                OPEN_AFTERS,
                "look at its after options to play " + best.get().title());
    }

    static Optional<Choice> bestRider(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        AgendaForecast untouched = AgendaForecast.of(game, seat);
        if (untouched.isEmpty()) return Optional.empty();
        double current = AgendaVoting.forecastWithItsBallot(game, seat).expectedUtility(seat);
        return seat.getActionCards().keySet().stream()
                .sorted()
                .filter(alias -> RIDER_TITLES.contains(SabotageWindow.title(alias)))
                .map(alias -> bestOutcome(untouched, current, seat, alias))
                .flatMap(Optional::stream)
                .filter(choice -> choice.gain() >= ActionCardPrior.keepPrior(choice.alias()))
                .max(Comparator.comparingDouble(Choice::gain));
    }

    private static Optional<Choice> bestOutcome(AgendaForecast untouched, double current, Player seat, String alias) {
        String title = SabotageWindow.title(alias);
        return untouched.outcomes().stream()
                .map(outcome -> new Choice(alias, title, outcome, gain(untouched, current, seat, title, outcome)))
                .max(Comparator.comparingDouble(Choice::gain));
    }

    private static double gain(AgendaForecast untouched, double current, Player seat, String title, String outcome) {
        return untouched.withRider(seat, title, outcome).expectedUtility(seat) - current;
    }

    private static Optional<AiDecision> abandonQueuedRider(AiTurnContext context) {
        boolean voting = !WAITING_PHASE.equalsIgnoreCase(context.game().getPhaseOfGame());
        if (!voting || !queuedRider(context)) return Optional.empty();
        return pressHidden(context, DECLINE, "drop the rider it queued, voting has started");
    }

    private static Optional<AiDecision> lockQueuedRider(AiTurnContext context) {
        if (!WAITING_PHASE.equalsIgnoreCase(context.game().getPhaseOfGame()) || !queuedRider(context)) {
            return Optional.empty();
        }
        return pressHidden(context, LOCK, "keep its rider queued");
    }

    private static boolean queuedRider(AiTurnContext context) {
        return AgendaQueue.queuedAfter(context.game(), context.seat())
                .filter(queued -> queued.startsWith(QUEUED_CARD))
                .map(queued -> StringUtils.removeStart(queued, QUEUED_CARD))
                .filter(alias -> RIDER_TITLES.contains(SabotageWindow.title(alias)))
                .isPresent();
    }

    private static Optional<AiDecision> placeRider(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || !AgendaClock.isInThisAgenda(context, prompt)) continue;
            List<PromptButton> options = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()))
                    .filter(button -> riderTitleOf(button).isPresent())
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            if (options.isEmpty()) continue;
            String title = riderTitleOf(options.getFirst()).orElseThrow();
            int playsBefore = entry(context).map(Entry::playsBefore).orElse(0);
            if (placed(game, seat, title) || canceled(game, seat, title, playsBefore)) continue;
            return Optional.of(AiDecision.press(prompt, bestPlacement(context, options, title), "place its " + title));
        }
        return Optional.empty();
    }

    private static PromptButton bestPlacement(AiTurnContext context, List<PromptButton> options, String title) {
        Game game = context.game();
        Player seat = context.seat();
        AgendaForecast untouched = AgendaForecast.of(game, seat);
        if (untouched.isEmpty()) return options.getFirst();
        double current = AgendaVoting.forecastWithItsBallot(game, seat).expectedUtility(seat);
        ToDoubleFunction<String> gain = outcome -> gain(untouched, current, seat, title, outcome);
        return options.stream()
                .max(Comparator.comparingDouble(button -> outcomesOf(game, button).stream()
                        .filter(untouched.outcomes()::contains)
                        .mapToDouble(gain)
                        .max()
                        .orElse(Double.NEGATIVE_INFINITY)))
                .orElseThrow();
    }

    static Optional<String> riderTitleOf(PromptButton button) {
        String id = button.handlerId();
        if (!id.startsWith(RIDER) && !id.startsWith(PLANET_RIDER)) return Optional.empty();
        String title = StringUtils.substringAfterLast(id, SEPARATOR);
        return RIDER_TITLES.contains(title) ? Optional.of(title) : Optional.empty();
    }

    static List<String> outcomesOf(Game game, PromptButton button) {
        String id = button.handlerId();
        String middle = StringUtils.substringBeforeLast(
                StringUtils.removeStart(StringUtils.removeStart(id, PLANET_RIDER), RIDER), SEPARATOR);
        if (id.startsWith(PLANET_RIDER)) {
            Player owner = game.getPlayerFromColorOrFaction(middle);
            return owner == null ? List.of() : List.copyOf(owner.getPlanets());
        }
        return List.of(StringUtils.substringAfter(middle, ";"));
    }

    private static boolean placed(Game game, Player seat, String title) {
        for (Map.Entry<String, String> votes : game.getCurrentAgendaVotes().entrySet()) {
            for (String piece : votes.getValue().split(";")) {
                if (piece.equalsIgnoreCase(seat.getFaction() + SEPARATOR + title)) return true;
            }
        }
        return false;
    }

    private static boolean canceled(Game game, Player seat, String title, int playsBefore) {
        return SabotageWindow.canceledPlay(game, seat, title, playsBefore);
    }

    private static boolean playedSince(Game game, Player seat, String title, int playsBefore) {
        String playerId = GameStats.getTrackedPlayerId(seat);
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = Math.max(0, playsBefore); index < plays.size(); index++) {
            GameStats.ActionCardPlay play = plays.get(index);
            if (title.equals(play.getActionCard()) && playerId != null && playerId.equals(play.getPlayerId())) {
                return true;
            }
        }
        return false;
    }

    private static long playedSeenAt(AiTurnContext context) {
        String key = PLAYED_KEY + context.game().getCurrentAgendaInfo();
        Optional<String> seen = context.memory().get(key).filter(StringUtils::isNumeric);
        if (seen.isPresent()) return Long.parseLong(seen.get());
        context.memory().put(key, String.valueOf(context.now()));
        return context.now();
    }

    private static Optional<Entry> entry(AiTurnContext context) {
        return context.memory().get(entryKey(context)).flatMap(Entry::decode);
    }

    private static String entryKey(AiTurnContext context) {
        return RIDER_KEY + context.game().getCurrentAgendaInfo();
    }

    private static Optional<AiDecision> pressHidden(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }
}
