package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.agenda.BallotProbe;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;

/**
 * The human-readable explanation of each agenda in a self-play run: one "AGENDA TRACE" block per agenda, printed when
 * the agenda closes. It lists every seat's ballot with the AI's reason and the ballot planner's numbers (BallotProbe),
 * every rider with what it was valued at and what it actually paid, every deal made or refused in the agenda window
 * (trades, vote promises, posted offers) with whether a promised vote was delivered, the other agenda decisions, every
 * change to a seat's trade goods, points, technologies or cards with the presses that preceded it, and per seat the
 * change over the window split into deal, rider, agenda effect and other.
 *
 * <p>Decisions and presses are keyed by the agenda's currentAgendaInfo, so ones heard before the tracker opens the
 * agenda still land in its block. Off with -Dai.selfplay.agenda.trace=false.
 */
final class AgendaTrace {

    static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("ai.selfplay.agenda.trace", "true"));
    static final String PREFIX = "AGENDA TRACE";
    private static final String AGENDA_PHASE = "agenda";
    private static final String SEND_TG = "sendTGTo_";
    private static final String RISKED_PREDICTIVE = "riskedPredictive";
    private static final String XXCHA_COMMANDER = "xxchacommander";

    // Seat state the trace follows, by index into Snap.values.
    static final String[] KINDS = {"tg", "comm", "vp", "tech", "ac", "pn", "cc"};
    private static final int TG = 0;
    private static final int COMM = 1;
    private static final int VP = 2;
    private static final int TECH = 3;
    private static final int AC = 4;
    private static final int PN = 5;
    private static final int CC = 6;

    // What each rider pays when its outcome wins, by the kinds above; a rider not listed pays on the board.
    private static final Map<String, int[]> RIDER_KINDS = Map.of(
            "imperial rider", new int[] {VP},
            "trade rider", new int[] {TG},
            "politics rider", new int[] {AC},
            "technology rider", new int[] {TECH, CC},
            "leadership rider", new int[] {CC},
            "galactic threat rider", new int[] {TECH},
            "keleres rider", new int[] {AC, TG});
    private static final Set<String> NOT_ACTION_CARDS = Set.of("galactic threat rider", "keleres rider");

    record Snap(int[] values, List<String> techs, boolean speaker) {

        static Snap of(Game game, Player seat) {
            int[] values = new int[KINDS.length];
            values[TG] = seat.getTg();
            values[COMM] = seat.getCommodities();
            values[VP] = seat.getTotalVictoryPoints();
            values[TECH] = seat.getTechs().size();
            values[AC] = seat.getActionCards().size();
            values[PN] = seat.getPromissoryNotes().size();
            values[CC] = seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
            boolean speaker = seat.getUserID() != null && seat.getUserID().equals(game.getSpeakerUserID());
            return new Snap(values, List.copyOf(seat.getTechs()), speaker);
        }

        boolean sameAs(Snap other) {
            return java.util.Arrays.equals(values, other.values) && speaker == other.speaker;
        }
    }

    enum Kind {
        BALLOT,
        RIDER,
        DEAL,
        CARD,
        OTHER
    }

    record Event(long at, String faction, Kind kind, String text) {}

    record Press(long at, String faction, String customId, String outcome) {}

    // One rider as the tracker saw it, with what it was valued at and when it was first seen.
    record RiderSeen(
            String faction,
            String rider,
            String outcome,
            double payoff,
            double chance,
            // Galactic Threat only: the technology Nekro expects to copy from seats voting that way (NaN otherwise).
            double threatWorth,
            long seenAt,
            int ballotsBefore) {}

    private record Flow(long at, boolean afterResolution, String text, Map<String, int[]> bySeat) {}

    private final Map<String, List<Event>> events = new HashMap<>();
    private final Map<String, Map<String, BallotProbe.Explanation>> ballots = new HashMap<>();
    private final Map<String, Set<String>> seenBallotReasons = new HashMap<>();
    private final Map<String, List<Press>> presses = new HashMap<>();
    private final Supplier<List<SelfPlayEnvironment.TradeRecord>> ledger;

    AgendaTrace(Supplier<List<SelfPlayEnvironment.TradeRecord>> ledger) {
        this.ledger = ledger;
    }

    static boolean inAgenda(Game game) {
        String phase = game.getPhaseOfGame();
        return phase != null && phase.toLowerCase(Locale.ROOT).startsWith(AGENDA_PHASE);
    }

    // Heard from the seat brains, on whatever thread decides; the ballot probe runs outside the lock.
    void decided(AiTurnContext context, AiDecision decision) {
        if (!ENABLED) return;
        Game game = context.game();
        if (game == null || !inAgenda(game)) return;
        String info = game.getCurrentAgendaInfo();
        if (info == null || info.isBlank()) return;
        String reason = reasonOf(decision);
        if (reason == null || mechanical(reason)) return;
        Kind kind = kindOf(decision, reason);
        String faction = context.faction();
        BallotProbe.Explanation explanation = null;
        if (kind == Kind.BALLOT && needsBallot(info, faction)) {
            try {
                explanation = BallotProbe.explain(game, context.seat()).orElse(null);
            } catch (RuntimeException e) {
                explanation = null;
            }
        }
        String text = kind == Kind.DEAL ? dealText(game, context.seat(), decision, reason) : reason;
        synchronized (this) {
            if (explanation != null)
                ballots.computeIfAbsent(info, key -> new LinkedHashMap<>()).putIfAbsent(faction, explanation);
            if (kind == Kind.BALLOT
                    && !seenBallotReasons
                            .computeIfAbsent(info, key -> new LinkedHashSet<>())
                            .add(faction + "|" + reason)) return;
            events.computeIfAbsent(info, key -> new ArrayList<>()).add(new Event(context.now(), faction, kind, text));
        }
    }

    synchronized void pressed(String info, long now, String faction, String customId, String outcome) {
        if (!ENABLED || info == null || info.isBlank()) return;
        presses.computeIfAbsent(info, key -> new ArrayList<>()).add(new Press(now, faction, customId, outcome));
    }

    private synchronized boolean needsBallot(String info, String faction) {
        return !ballots.getOrDefault(info, Map.of()).containsKey(faction);
    }

    private static String reasonOf(AiDecision decision) {
        return switch (decision) {
            case AiDecision.Press press -> press.reason();
            case AiDecision.Unsure unsure -> "UNSURE: " + unsure.reason();
            case AiDecision.Post post -> "post: " + post.text();
            case AiDecision.Propose propose -> "propose: " + propose.deal().purpose();
            case AiDecision.Announce announce -> "announce: " + announce.text();
            default -> null;
        };
    }

    private static boolean mechanical(String reason) {
        return reason.startsWith("exhaust a planet for votes")
                || reason.startsWith("confirm its votes")
                || reason.startsWith("finish casting its votes")
                || reason.startsWith("look at ")
                || reason.startsWith("pass on playing")
                || reason.startsWith("keep its ")
                || reason.startsWith("confirm it plays none of the agenda shenanigans");
    }

    private static Kind kindOf(AiDecision decision, String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        if (decision instanceof AiDecision.Post || decision instanceof AiDecision.Propose) return Kind.DEAL;
        if (lower.startsWith("trade:") || lower.contains("promise") || lower.contains(" deal")) return Kind.DEAL;
        if (lower.startsWith("vote for")
                || lower.startsWith("abstain")
                || lower.startsWith("cast ")
                || lower.startsWith("its outcome is not offered")
                || lower.startsWith("spend a trade good for")) return Kind.BALLOT;
        if (lower.contains("rider") || lower.contains("predict") || lower.contains("galactic threat"))
            return Kind.RIDER;
        if (lower.startsWith("queue ") || lower.startsWith("play ") || lower.contains("veto")) return Kind.CARD;
        return Kind.OTHER;
    }

    // What a deal decision is about: the items an offer carries, or the promise a button answers.
    private static String dealText(Game game, Player seat, AiDecision decision, String reason) {
        try {
            if (decision instanceof AiDecision.Post post)
                return "post: " + StringUtils.abbreviate(clean(post.text()), 300);
            if (!(decision instanceof AiDecision.Press press)) return reason;
            if (reason.startsWith(SelfPlayEnvironment.SEND_PREFIX)) {
                Player partner = game.getPlayerFromColorOrFaction(StringUtils.substringAfterLast(reason, " to "));
                return partner == null ? reason : reason + " " + items(seat.getTransactionItemsWithPlayer(partner));
            }
            if (reason.contains(" offer from ")) {
                Player offerer = game.getPlayerFromColorOrFaction(StringUtils.substringAfterLast(reason, " from "));
                return offerer == null ? reason : reason + " " + items(offerer.getTransactionItemsWithPlayer(seat));
            }
            String handler = press.button().handlerId();
            if (handler.startsWith("aiPromiseYes_") || handler.startsWith("aiPromiseNo_")) {
                String id = StringUtils.substringAfter(handler, "_");
                return reason + " "
                        + PromiseBook.find(game, id)
                                .map(AgendaTrace::promiseText)
                                .orElse(id);
            }
        } catch (RuntimeException e) {
            return reason + " (items unreadable: " + e.getClass().getSimpleName() + ")";
        }
        return reason;
    }

    // Emoji keep their names (the self-play harness renders them as <Name>), mentions and channel links are dropped.
    static String clean(String text) {
        return text.replaceAll("<a?:([A-Za-z_0-9]+):[0-9]+>", "$1 ")
                .replaceAll("<[@#][!&]?[0-9]+>", "")
                .replaceAll("<([A-Za-z][A-Za-z_0-9' -]*)>", "$1 ")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String items(List<String> items) {
        if (items.isEmpty()) return "{nothing}";
        Map<String, List<String>> bySide = new LinkedHashMap<>();
        for (String item : items) {
            String[] parts = item.split("_", 4);
            if (parts.length < 4) continue;
            String from = StringUtils.removeStart(parts[0], "sending");
            String to = StringUtils.removeStart(parts[1], "receiving");
            String what =
                    switch (parts[2]) {
                        case "TGs" -> parts[3] + " TG";
                        case "Comms" -> parts[3] + " comm";
                        case "ACs" -> parts[3].startsWith("generic") ? parts[3].substring(7) + " AC" : "AC#" + parts[3];
                        case "PNs" -> "PN " + parts[3];
                        case "details" ->
                            "note \"" + StringUtils.abbreviate(parts[3].replace("fin777", " "), 120) + "\"";
                        default -> parts[2] + " " + parts[3];
                    };
            bySide.computeIfAbsent(from + " gives " + to, key -> new ArrayList<>())
                    .add(what);
        }
        List<String> sides = new ArrayList<>();
        bySide.forEach((side, things) -> sides.add(side + " " + String.join(", ", things)));
        return "{" + String.join("; ", sides) + "}";
    }

    static String promiseText(Promise promise) {
        return switch (promise.term()) {
            case VOTE_FOR ->
                String.format(
                        "[%s promises %s its votes%s for %s, %s pays %d TG; proposed by %s; %s]",
                        promise.promiser(),
                        promise.promisee(),
                        promise.votes() > 0 ? " (" + promise.votes() + ")" : "",
                        promise.target(),
                        promise.promisee(),
                        promise.price(),
                        promise.proposer(),
                        promise.status());
            case NOT_AGAINST ->
                String.format(
                        "[%s promises %s not to vote against %s, %s pays %d TG; proposed by %s; %s]",
                        promise.promiser(),
                        promise.promisee(),
                        promise.target(),
                        promise.promisee(),
                        promise.price(),
                        promise.proposer(),
                        promise.status());
            case DISCARD_AGENDA ->
                String.format(
                        "[%s owes %s %d TG for discarding the agenda with %s; %s]",
                        promise.promisee(), promise.promiser(), promise.price(), promise.target(), promise.status());
            case NO_ATTACK, STAY_OUT ->
                String.format(
                        "[%s promises %s %s %s for %d TG; %s]",
                        promise.promiser(),
                        promise.promisee(),
                        promise.term(),
                        promise.target(),
                        promise.price(),
                        promise.status());
        };
    }

    Window open(Game game, String info, long now) {
        return new Window(game, info, now);
    }

    // The trace state of one open agenda, from reveal to close.
    final class Window {
        private final String info;
        private final long openedAt;
        private final Map<String, Snap> atReveal = new LinkedHashMap<>();
        private Map<String, Snap> last = new LinkedHashMap<>();
        private Map<String, Snap> beforeResolution;
        private long resolvedAt = -1;
        private final int ledgerStart;
        private int ledgerAtResolution = -1;
        private Map<String, Map<String, Double>> chancesAtReveal = Map.of();
        private Map<String, String> splitsAtReveal = Map.of();
        private final Map<String, Promise> promises = new LinkedHashMap<>();
        private final Map<String, Long> promiseAcceptedAt = new LinkedHashMap<>();
        private final List<String> changes = new ArrayList<>();
        private final List<Flow> sends = new ArrayList<>();
        private int pressesSeen;
        private String agendaKey = "";
        // The engine's vote count per seat at reveal (planets plus other sources such as Predictive Intelligence).
        private final Map<String, Integer> votesAtReveal = new LinkedHashMap<>();
        // Factions in the engine's voting order, refreshed until the agenda resolves (Hack Election can reverse it).
        private List<String> votingOrder = List.of();
        private final Set<String> usedPredictive = new LinkedHashSet<>();

        private Window(Game game, String info, long now) {
            this.info = info;
            this.openedAt = now;
            for (Player seat : game.getRealPlayers()) {
                atReveal.put(seat.getFaction(), Snap.of(game, seat));
                votesAtReveal.put(seat.getFaction(), engineVotes(game, seat));
            }
            last = new LinkedHashMap<>(atReveal);
            ledgerStart = ledgerSize();
            votingOrder = votingOrderOf(game);
        }

        long openedAt() {
            return openedAt;
        }

        void observe(Game game, long now, boolean resolved) {
            if (!ENABLED) return;
            if (chancesAtReveal.isEmpty()) chancesAtReveal = chancesOf(game);
            if (agendaKey.isBlank()) agendaKey = ti4.ai.agenda.AgendaUtilityProbe.currentAgenda(game);
            if (splitsAtReveal.isEmpty()) splitsAtReveal = splitsOf(game, agendaKey);
            Map<String, Snap> current = new LinkedHashMap<>();
            for (Player seat : game.getRealPlayers()) current.put(seat.getFaction(), Snap.of(game, seat));
            if (!resolved) {
                beforeResolution = current;
                List<String> order = votingOrderOf(game);
                if (!order.isEmpty()) votingOrder = order;
                String risked = game.getStoredValue(RISKED_PREDICTIVE);
                for (Player seat : game.getRealPlayers())
                    if (risked.contains(seat.getFaction())) usedPredictive.add(seat.getFaction());
            } else if (resolvedAt < 0) {
                resolvedAt = now;
                ledgerAtResolution = ledgerSize();
                if (beforeResolution == null) beforeResolution = last;
            }
            recordChanges(now, current);
            last = current;
            recordPromises(game, now);
        }

        // Per-seat value breakdown at reveal, for agendas the probe can split (Ixthian Artifact today).
        private Map<String, String> splitsOf(Game game, String agenda) {
            Map<String, String> splits = new LinkedHashMap<>();
            try {
                for (Player seat : game.getRealPlayers()) {
                    String split = ti4.ai.agenda.AgendaUtilityProbe.artifactSplit(game, seat, agenda);
                    if (split.isEmpty()) return Map.of();
                    splits.put(seat.getFaction(), split);
                }
            } catch (RuntimeException e) {
                return Map.of();
            }
            return splits;
        }

        private Map<String, Map<String, Double>> chancesOf(Game game) {
            Map<String, Map<String, Double>> chances = new LinkedHashMap<>();
            try {
                for (Player seat : game.getRealPlayers()) {
                    Map<String, Double> seen = BallotProbe.chances(game, seat);
                    if (seen.isEmpty()) return Map.of();
                    chances.put(seat.getFaction(), seen);
                }
            } catch (RuntimeException e) {
                return Map.of();
            }
            return chances;
        }

        private void recordChanges(long now, Map<String, Snap> current) {
            List<Press> heard;
            synchronized (AgendaTrace.this) {
                List<Press> all = presses.getOrDefault(info, List.of());
                heard = List.copyOf(all.subList(Math.min(pressesSeen, all.size()), all.size()));
                pressesSeen = all.size();
            }
            for (Map.Entry<String, Snap> entry : current.entrySet()) {
                Snap before = last.get(entry.getKey());
                if (before == null || before.sameAs(entry.getValue())) continue;
                String presses = heard.stream()
                        .filter(press -> press.faction().equals(entry.getKey()))
                        .map(press -> StringUtils.abbreviate(press.customId(), 70))
                        .distinct()
                        .limit(4)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(
                                heard.isEmpty()
                                        ? "none"
                                        : "none of its own; others pressed "
                                                + heard.stream()
                                                        .map(press -> press.faction() + " "
                                                                + StringUtils.abbreviate(press.customId(), 50))
                                                        .distinct()
                                                        .limit(3)
                                                        .reduce((a, b) -> a + ", " + b)
                                                        .orElse(""));
                changes.add(String.format(
                        "%s %-8s %s  [presses this step: %s]",
                        minutes(now), entry.getKey(), change(before, entry.getValue()), presses));
            }
            heard.forEach(this::recordSend);
        }

        // "sendTGTo_<receiver>_<tg|tg3|comm|comm3|debt|debt3>": the agenda-phase transfer buttons, which also pay
        // debts.
        private void recordSend(Press press) {
            String rest = StringUtils.substringAfter(press.customId(), SEND_TG);
            if (rest.isEmpty()) return;
            String to = StringUtils.substringBefore(rest, "_");
            String what = StringUtils.substringAfter(rest, "_");
            int amount = what.endsWith("3") ? 3 : 1;
            Map<String, int[]> bySeat = new LinkedHashMap<>();
            int[] giver = bySeat.computeIfAbsent(press.faction(), key -> new int[KINDS.length]);
            int[] taker = bySeat.computeIfAbsent(to, key -> new int[KINDS.length]);
            String text;
            if (what.startsWith("tg")) {
                giver[TG] -= amount;
                taker[TG] += amount;
                text = amount + " TG";
            } else if (what.startsWith("comm")) {
                giver[COMM] -= amount;
                taker[TG] += amount;
                text = amount + " comm";
            } else {
                text = "erases " + amount + " debt";
            }
            sends.add(new Flow(
                    press.at(),
                    resolvedAt >= 0 && press.at() >= resolvedAt,
                    String.format("%s SEND %s -> %s %s", minutes(press.at()), press.faction(), to, text),
                    bySeat));
        }

        private void recordPromises(Game game, long now) {
            if (agendaKey.isBlank()) return;
            for (Promise promise : PromiseBook.all(game)) {
                if (promise.term() != Promise.Term.VOTE_FOR || !agendaKey.equals(promise.agenda())) continue;
                if (promise.round() != game.getRound()) continue;
                promises.put(promise.id(), promise);
                boolean accepted = promise.status() == Promise.Status.ACCEPTED
                        || promise.status() == Promise.Status.KEPT
                        || promise.status() == Promise.Status.BROKEN;
                if (accepted) promiseAcceptedAt.putIfAbsent(promise.id(), now);
            }
        }

        private int ledgerSize() {
            List<SelfPlayEnvironment.TradeRecord> records = ledger.get();
            if (records == null) return 0;
            synchronized (records) {
                return records.size();
            }
        }

        private List<SelfPlayEnvironment.TradeRecord> ledgerSince(int from, int to) {
            List<SelfPlayEnvironment.TradeRecord> records = ledger.get();
            if (records == null) return List.of();
            synchronized (records) {
                int end = to < 0 ? records.size() : Math.min(to, records.size());
                return from >= end ? List.of() : List.copyOf(records.subList(from, end));
            }
        }

        private String minutes(long at) {
            return String.format("t+%.1fm", Math.max(0, at - openedAt) / 60_000.0);
        }

        String close(
                Game game,
                String gameName,
                AgendaTracker.Agenda agenda,
                List<RiderSeen> riders,
                Map<String, Map<String, Double>> utilities) {
            List<Event> heard;
            Map<String, BallotProbe.Explanation> explained;
            synchronized (AgendaTrace.this) {
                heard = events.getOrDefault(info, List.of());
                explained = ballots.getOrDefault(info, Map.of());
                events.remove(info);
                ballots.remove(info);
                seenBallotReasons.remove(info);
                presses.remove(info);
            }
            Map<String, Snap> atClose = new LinkedHashMap<>();
            for (Player seat : game.getRealPlayers()) atClose.put(seat.getFaction(), Snap.of(game, seat));
            List<Flow> flows = flows(game);
            List<String> out = new ArrayList<>();
            out.add(String.format(
                    "%s %s r%d #%d %s (%s) | speaker %s | outcome %s | reveal-to-resolution %.1fm%s",
                    PREFIX,
                    gameName,
                    agenda.round(),
                    agenda.number(),
                    agendaName(agenda.agenda()),
                    agenda.agenda().isBlank() ? agenda.info() : agenda.agenda(),
                    agenda.speaker(),
                    agenda.resolved() ? agenda.outcome() : "UNRESOLVED",
                    agenda.waitMillis() / 60_000.0,
                    agenda.refereeTieBreaks() + agenda.fallbackTieBreaks() > 0
                            ? " | tie broken by referee " + agenda.refereeTieBreaks() + ", fallback "
                                    + agenda.fallbackTieBreaks()
                            : ""));
            out.add("  " + votingOrderLine(game, agenda, heard));
            out.add("  BALLOTS (votes cast; AI reason; planner view: own utility in TG, forecast win chance)");
            for (Player seat : game.getRealPlayers()) {
                String faction = seat.getFaction();
                String cast = agenda.votesBySeat().getOrDefault(faction, "none");
                String reasons = heard.stream()
                        .filter(event ->
                                event.kind() == Kind.BALLOT && event.faction().equals(faction))
                        .map(event -> "\"" + event.text() + "\"")
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(notHeard(game, seat, agenda));
                out.add(String.format("    %-8s cast %-14s %s", faction, cast, reasons));
                BallotProbe.Explanation why = explained.get(faction);
                if (why != null) out.addAll(explanation(why));
                Map<String, Double> reveal = utilities.get(faction);
                Map<String, Double> chances = chancesAtReveal.get(faction);
                if (reveal != null || chances != null)
                    out.add("             at reveal: reference utility " + values(reveal, "%+.1f") + "; forecast "
                            + values(chances, "pct"));
                String split = splitsAtReveal.get(faction);
                if (split != null) out.add("             split: " + split);
            }
            out.add("  RIDERS");
            if (riders.isEmpty()) out.add("    none");
            for (RiderSeen rider : riders) out.add("    " + riderLine(rider, agenda, atClose));
            out.add("  DEALS (agenda window)");
            List<String> deals = dealLines(heard, flows, agenda);
            if (deals.isEmpty()) out.add("    none");
            deals.forEach(line -> out.add("    " + line));
            List<Event> other = heard.stream()
                    .filter(event ->
                            event.kind() == Kind.CARD || event.kind() == Kind.OTHER || event.kind() == Kind.RIDER)
                    .toList();
            if (!other.isEmpty()) {
                out.add("  OTHER AGENDA DECISIONS");
                other.stream()
                        .map(event -> String.format(
                                "%s %-8s %s: %s",
                                minutes(event.at()),
                                event.faction(),
                                event.kind().name().toLowerCase(Locale.ROOT),
                                StringUtils.abbreviate(event.text(), 200)))
                        .distinct()
                        .forEach(line -> out.add("    " + line));
            }
            out.add("  STATE CHANGES (tg comm vp tech ac pn cc speaker)");
            if (changes.isEmpty()) out.add("    none");
            changes.forEach(line -> out.add("    " + line));
            out.add("  ATTRIBUTION reveal->close: total = deal + rider + agenda effect + other");
            out.addAll(attribution(atClose, flows, riders, agenda));
            return String.join(System.lineSeparator(), out);
        }

        // Why a seat made no ballot decision: the engine skips a seat with no votes to cast (all planets exhausted, a
        // rider placed without the Xxcha commander, Galactic Threat) and never asks it.
        private String notHeard(Game game, Player seat, AgendaTracker.Agenda agenda) {
            if (BallotProbe.cannotVote(game, seat)) return "cannot vote (Galactic Threat)";
            if (placedARider(game, seat, agenda)) return "skipped by the engine: placed a rider, so it cannot vote";
            if (votesAtReveal.getOrDefault(seat.getFaction(), -1) == 0)
                return "skipped by the engine: no votes to cast (0 at reveal)";
            return "no ballot decision heard";
        }

        private static boolean placedARider(Game game, Player seat, AgendaTracker.Agenda agenda) {
            if (game.playerHasLeaderUnlockedOrAlliance(seat, XXCHA_COMMANDER)) return false;
            return agenda.riders().stream()
                    .anyMatch(rider -> rider.faction().equals(seat.getFaction())
                            && !AgendaTracker.isGalacticThreat(rider.rider()));
        }

        // "VOTING ORDER: Xxcha (6) 1 For, Nekro (0) -, ...; tally For 5, Against 5; speaker Sol broke the 5-5 tie:
        // Against": every seat in the engine's voting order with its vote count at reveal and its ballot.
        String votingOrderLine(Game game, AgendaTracker.Agenda agenda, List<Event> heard) {
            List<String> order = new ArrayList<>(votingOrder);
            for (Player seat : game.getRealPlayers())
                if (!order.contains(seat.getFaction())) order.add(seat.getFaction());
            List<String> seats = new ArrayList<>();
            for (String faction : order) {
                Player seat = game.getPlayerFromColorOrFaction(faction);
                Integer atReveal = votesAtReveal.get(faction);
                seats.add(String.format(
                        "%s (%s) %s",
                        StringUtils.capitalize(faction),
                        atReveal == null ? "?" : atReveal,
                        seat == null ? "?" : ballotText(game, seat, agenda, heard)));
            }
            Map<String, Integer> tally = tally(agenda);
            String tallied = tally.isEmpty()
                    ? "no votes cast"
                    : "tally "
                            + String.join(
                                    ", ",
                                    tally.entrySet().stream()
                                            .map(entry ->
                                                    StringUtils.capitalize(entry.getKey()) + " " + entry.getValue())
                                            .toList());
            return "VOTING ORDER: " + String.join(", ", seats) + "; " + tallied + tieText(agenda, tally, heard);
        }

        private String ballotText(Game game, Player seat, AgendaTracker.Agenda agenda, List<Event> heard) {
            String faction = seat.getFaction();
            String cast = agenda.votesBySeat().getOrDefault(faction, "");
            if (!cast.isBlank()) {
                List<String> pieces = new ArrayList<>();
                for (String piece : cast.split("\\+"))
                    pieces.add(StringUtils.substringAfterLast(piece, " ") + " "
                            + StringUtils.capitalize(StringUtils.substringBeforeLast(piece, " ")));
                return String.join(" + ", pieces)
                        + (usedPredictive(faction, heard) ? " (PI)" : "")
                        + (seat.hasAbility("galactic_threat") ? " (Xxcha commander lifts Galactic Threat)" : "");
            }
            if (BallotProbe.cannotVote(game, seat)) return "-";
            if (placedARider(game, seat, agenda)) return "skipped (rider)";
            if (votesAtReveal.getOrDefault(faction, -1) == 0) return "skipped (no votes)";
            boolean abstained = heard.stream()
                    .anyMatch(event -> event.kind() == Kind.BALLOT
                            && event.faction().equals(faction)
                            && event.text().toLowerCase(Locale.ROOT).startsWith("abstain"));
            return abstained ? "abstain" : "no vote";
        }

        private boolean usedPredictive(String faction, List<Event> heard) {
            return usedPredictive.contains(faction)
                    || heard.stream()
                            .anyMatch(event -> event.faction().equals(faction)
                                    && event.text().contains("Predictive Intelligence"));
        }

        private static Map<String, Integer> tally(AgendaTracker.Agenda agenda) {
            Map<String, Integer> tally = new LinkedHashMap<>();
            for (String cast : agenda.votesBySeat().values()) {
                for (String piece : cast.split("\\+")) {
                    String votes = StringUtils.substringAfterLast(piece, " ");
                    if (!StringUtils.isNumeric(votes)) continue;
                    tally.merge(StringUtils.substringBeforeLast(piece, " "), Integer.parseInt(votes), Integer::sum);
                }
            }
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(tally.entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            Map<String, Integer> ordered = new LinkedHashMap<>();
            sorted.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            return ordered;
        }

        private static String tieText(AgendaTracker.Agenda agenda, Map<String, Integer> tally, List<Event> heard) {
            if (!agenda.resolved()) return "; unresolved";
            String outcome = StringUtils.capitalize(agenda.outcome());
            List<Integer> totals = new ArrayList<>(tally.values());
            boolean tied =
                    totals.isEmpty() || totals.size() > 1 && totals.get(0).equals(totals.get(1));
            if (!tied) {
                String leader = tally.keySet().iterator().next();
                return leader.equalsIgnoreCase(agenda.outcome()) ? "" : "; resolved " + outcome + " against the tally";
            }
            int top = totals.isEmpty() ? 0 : totals.getFirst();
            String tie = totals.isEmpty()
                    ? "the no-vote tie"
                    : "the "
                            + String.join(
                                    "-",
                                    java.util.Collections.nCopies(
                                            (int) totals.stream()
                                                    .filter(total -> total == top)
                                                    .count(),
                                            String.valueOf(top)))
                            + " tie";
            if (agenda.refereeTieBreaks() > 0) return "; the referee broke " + tie + ": " + outcome;
            if (agenda.fallbackTieBreaks() > 0)
                return "; speaker " + StringUtils.capitalize(agenda.speaker()) + " broke " + tie
                        + " through its stall fallback: " + outcome;
            boolean speakerBroke = heard.stream()
                    .anyMatch(event -> event.faction().equals(agenda.speaker())
                            && event.text().startsWith("break the agenda tie"));
            return "; " + (speakerBroke ? "speaker " + StringUtils.capitalize(agenda.speaker()) : "the speaker")
                    + " broke " + tie + ": " + outcome;
        }

        private List<String> explanation(BallotProbe.Explanation why) {
            List<String> lines = new ArrayList<>();
            String planned = why.planned().isBlank()
                    ? "abstain"
                    : why.planned() + " x" + why.plannedVotes() + (why.promised() ? " (promised)" : "");
            lines.add(String.format(
                    "             planner: %s | votes ready %d, cap %d%s%s%s",
                    planned,
                    why.available(),
                    why.cap(),
                    why.keepsAVoteBack() ? " (keeps a vote back)" : "",
                    why.finalAgenda() ? " (final agenda: all in)" : "",
                    why.sitsOut().isBlank() ? "" : " | " + why.sitsOut()));
            lines.add("             own utility " + values(why.utility(), "%+.1f") + " | chance if abstaining "
                    + values(why.chanceIfAbstain(), "pct") + " -> with its ballot "
                    + values(why.chanceWithBallot(), "pct"));
            if (!why.chanceIfAbstain().isEmpty())
                lines.add(String.format(
                        "             value: abstain %+.2f, its ballot %+.2f (gain %+.2f, needs > %.2f; Galactic Threat"
                                + " cost %.2f) | best %s x%d %+.2f over abstain",
                        why.abstainValue(),
                        why.ballotValue(),
                        why.ballotValue() - why.abstainValue(),
                        why.finalAgenda() ? 0 : 0.5,
                        why.threatCost(),
                        why.bestOutcome().isBlank() ? "-" : why.bestOutcome(),
                        why.bestVotes(),
                        why.bestValue() - why.abstainValue()));
            if (!why.curve().isEmpty())
                lines.add("             gain over abstain by votes (its chance): " + String.join(" ", why.curve()));
            return lines;
        }

        private static boolean won(RiderSeen rider, AgendaTracker.Agenda agenda) {
            return agenda.riders().stream()
                    .filter(placed -> placed.faction().equals(rider.faction())
                            && placed.rider().equals(rider.rider())
                            && placed.outcome().equals(rider.outcome()))
                    .findFirst()
                    .map(AgendaTracker.Rider::won)
                    .orElse(agenda.resolved() && rider.outcome().equalsIgnoreCase(agenda.outcome()));
        }

        // What the tracker scored the won rider at: the copied technology for Galactic Threat, else its payoff.
        private static String realizedWorth(RiderSeen rider, AgendaTracker.Agenda agenda) {
            return agenda.riders().stream()
                    .filter(placed -> placed.faction().equals(rider.faction())
                            && placed.rider().equals(rider.rider())
                            && placed.outcome().equals(rider.outcome()))
                    .findFirst()
                    .map(placed -> String.format(" (worth %.1f TG)", placed.payoff()))
                    .orElse("");
        }

        private String riderLine(RiderSeen rider, AgendaTracker.Agenda agenda, Map<String, Snap> atClose) {
            boolean won = won(rider, agenda);
            String name = rider.rider().toLowerCase(Locale.ROOT).trim();
            String paid = "-";
            if (won && beforeResolution != null) {
                Snap before = beforeResolution.get(rider.faction());
                Snap after = atClose.get(rider.faction());
                paid = before == null || after == null ? "?" : change(before, after);
            }
            return String.format(
                    "%-8s %s on %s | seen %s after %d ballot%s | its forecast chance %s%s | valued %.1f TG%s | %s%s | owner"
                            + " change after resolution: %s",
                    rider.faction(),
                    rider.rider(),
                    rider.outcome(),
                    minutes(rider.seenAt()),
                    rider.ballotsBefore(),
                    rider.ballotsBefore() == 1 ? "" : "s",
                    Double.isNaN(rider.chance()) ? "-" : String.format("%.0f%%", 100 * rider.chance()),
                    Double.isNaN(rider.threatWorth())
                            ? ""
                            : String.format(", expected copy through other voters %.1f TG", rider.threatWorth()),
                    rider.payoff(),
                    NOT_ACTION_CARDS.contains(name) || !name.endsWith("rider") ? "" : ", cost 1 action card",
                    won ? "WON" : agenda.resolved() ? "lost" : "unresolved",
                    won ? realizedWorth(rider, agenda) : "",
                    paid);
        }

        // Deals in the window: accepted trades from the ledger and vote promises, each a TG/card flow per seat.
        private List<Flow> flows(Game game) {
            List<Flow> flows = new ArrayList<>();
            int split = ledgerAtResolution < 0 ? Integer.MAX_VALUE : ledgerAtResolution;
            List<SelfPlayEnvironment.TradeRecord> trades = ledgerSince(ledgerStart, -1);
            for (int i = 0; i < trades.size(); i++) {
                SelfPlayEnvironment.TradeRecord trade = trades.get(i);
                Map<String, int[]> bySeat = new LinkedHashMap<>();
                for (String item : trade.items()) applyItem(item, bySeat);
                flows.add(new Flow(
                        -1,
                        ledgerStart + i >= split,
                        String.format(
                                "TRADE accepted %s -> %s%s %s",
                                trade.from(),
                                trade.to(),
                                trade.label().isBlank() ? "" : " (" + trade.label() + ")",
                                items(trade.items())),
                        bySeat));
            }
            for (Promise promise : promises.values()) {
                Long accepted = promiseAcceptedAt.get(promise.id());
                if (accepted == null || promise.price() <= 0) continue;
                Map<String, int[]> bySeat = new LinkedHashMap<>();
                bySeat.computeIfAbsent(promise.promisee(), key -> new int[KINDS.length])[TG] -= promise.price();
                bySeat.computeIfAbsent(promise.promiser(), key -> new int[KINDS.length])[TG] += promise.price();
                flows.add(new Flow(accepted, resolvedAt >= 0 && accepted >= resolvedAt, "PROMISE paid", bySeat));
            }
            flows.addAll(sends);
            return flows;
        }

        private static void applyItem(String item, Map<String, int[]> bySeat) {
            String[] parts = item.split("_", 4);
            if (parts.length < 4) return;
            String from = StringUtils.removeStart(parts[0], "sending");
            String to = StringUtils.removeStart(parts[1], "receiving");
            int[] giver = bySeat.computeIfAbsent(from, key -> new int[KINDS.length]);
            int[] taker = bySeat.computeIfAbsent(to, key -> new int[KINDS.length]);
            switch (parts[2]) {
                case "TGs" -> {
                    int amount = number(parts[3]);
                    giver[TG] -= amount;
                    taker[TG] += amount;
                }
                case "Comms" -> {
                    int amount = number(parts[3]);
                    giver[COMM] -= amount;
                    taker[TG] += amount;
                }
                case "ACs" -> {
                    int amount = parts[3].startsWith("generic") ? number(parts[3].substring(7)) : 1;
                    giver[AC] -= amount;
                    taker[AC] += amount;
                }
                case "PNs" -> {
                    giver[PN]--;
                    taker[PN]++;
                }
                default -> {}
            }
        }

        private static int number(String text) {
            return StringUtils.isNumeric(text) ? Integer.parseInt(text) : 0;
        }

        private List<String> dealLines(List<Event> heard, List<Flow> flows, AgendaTracker.Agenda agenda) {
            List<String> lines = new ArrayList<>();
            heard.stream()
                    .filter(event -> event.kind() == Kind.DEAL)
                    .forEach(event -> lines.add(String.format(
                            "%s %-8s %s",
                            minutes(event.at()), event.faction(), StringUtils.abbreviate(event.text(), 400))));
            flows.stream()
                    .filter(flow ->
                            flow.text().startsWith("TRADE") || flow.text().contains(" SEND "))
                    .forEach(flow -> lines.add((flow.afterResolution() ? "after resolution " : "") + flow.text()));
            for (Promise promise : promises.values()) {
                lines.add("VOTE PROMISE " + promiseText(promise) + " | " + delivery(promise, agenda));
            }
            return lines;
        }

        private String delivery(Promise promise, AgendaTracker.Agenda agenda) {
            if (!promiseAcceptedAt.containsKey(promise.id())) return "never accepted";
            String cast = agenda.votesBySeat().getOrDefault(promise.promiser(), "");
            if (cast.isBlank()) return "NOT DELIVERED: " + promise.promiser() + " cast no votes";
            boolean onTarget = java.util.Arrays.stream(cast.split("\\+"))
                    .anyMatch(piece -> piece.toLowerCase(Locale.ROOT)
                            .startsWith(promise.target().toLowerCase(Locale.ROOT) + " "));
            return (onTarget ? "DELIVERED: " : "BROKEN: ") + promise.promiser() + " cast " + cast;
        }

        private List<String> attribution(
                Map<String, Snap> atClose, List<Flow> flows, List<RiderSeen> riders, AgendaTracker.Agenda agenda) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Snap> entry : atClose.entrySet()) {
                String faction = entry.getKey();
                Snap reveal = atReveal.get(faction);
                if (reveal == null) continue;
                int[] total = minus(entry.getValue().values(), reveal.values());
                int[] deal = new int[KINDS.length];
                int[] dealAfter = new int[KINDS.length];
                for (Flow flow : flows) {
                    int[] mine = flow.bySeat().get(faction);
                    if (mine == null) continue;
                    add(deal, mine);
                    if (flow.afterResolution()) add(dealAfter, mine);
                }
                int[] rider = new int[KINDS.length];
                int[] effect = new int[KINDS.length];
                if (beforeResolution != null && beforeResolution.get(faction) != null) {
                    int[] resolution = minus(
                            minus(
                                    entry.getValue().values(),
                                    beforeResolution.get(faction).values()),
                            dealAfter);
                    for (RiderSeen seen : riders) {
                        if (!seen.faction().equals(faction)) continue;
                        String name = seen.rider().toLowerCase(Locale.ROOT).trim();
                        if (!NOT_ACTION_CARDS.contains(name) && name.endsWith("rider")) rider[AC]--;
                        boolean won = won(seen, agenda);
                        if (!won) continue;
                        for (int kind : RIDER_KINDS.getOrDefault(name, new int[0])) {
                            rider[kind] += resolution[kind];
                            resolution[kind] = 0;
                        }
                    }
                    effect = resolution;
                }
                int[] other = minus(minus(minus(total, deal), rider), effect);
                String speaker = reveal.speaker() == entry.getValue().speaker()
                        ? ""
                        : entry.getValue().speaker() ? ", gained speaker" : ", lost speaker";
                lines.add(String.format(
                        "    %-8s total %s%s | deal %s | rider %s | agenda %s | other %s",
                        faction, delta(total), speaker, delta(deal), delta(rider), delta(effect), delta(other)));
                List<String> gained = new ArrayList<>(entry.getValue().techs());
                gained.removeAll(reveal.techs());
                if (!gained.isEmpty()) lines.add("             techs gained: " + gained);
            }
            return lines;
        }
    }

    private static int engineVotes(Game game, Player seat) {
        try {
            return AgendaHelper.getVoteTotal(seat, game)[0];
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static List<String> votingOrderOf(Game game) {
        try {
            return AgendaHelper.getVotingOrder(game).stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(seat -> game.getRealPlayers().contains(seat))
                    .map(Player::getFaction)
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    static String agendaName(String agenda) {
        if (agenda == null || agenda.isBlank()) return "?";
        try {
            ti4.model.AgendaModel model = ti4.model.Mapper.getAgenda(agenda);
            return model == null ? agenda : model.getName();
        } catch (RuntimeException e) {
            return agenda;
        }
    }

    private static String values(Map<String, Double> values, String format) {
        if (values == null || values.isEmpty()) return "-";
        List<String> parts = new ArrayList<>();
        values.forEach((key, value) -> parts.add(key + " "
                + ("pct".equals(format) ? String.format("%.0f%%", 100 * value) : String.format(format, value))));
        return String.join(", ", parts);
    }

    private static String change(Snap before, Snap after) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < KINDS.length; i++) {
            if (before.values()[i] == after.values()[i]) continue;
            if (i == TECH) {
                List<String> gained = new ArrayList<>(after.techs());
                gained.removeAll(before.techs());
                parts.add("tech +" + gained);
                continue;
            }
            parts.add(KINDS[i] + " " + before.values()[i] + "->" + after.values()[i]);
        }
        if (before.speaker() != after.speaker()) parts.add(after.speaker() ? "gains speaker" : "loses speaker");
        return parts.isEmpty() ? "no change" : String.join(", ", parts);
    }

    private static String delta(int[] values) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < KINDS.length; i++)
            if (values[i] != 0) parts.add(String.format("%s %+d", KINDS[i], values[i]));
        return parts.isEmpty() ? "-" : String.join(" ", parts);
    }

    private static int[] minus(int[] a, int[] b) {
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] - b[i];
        return out;
    }

    private static void add(int[] into, int[] values) {
        for (int i = 0; i < into.length; i++) into[i] += values[i];
    }
}
