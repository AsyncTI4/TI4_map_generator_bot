package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.agenda.AgendaUtilityProbe;
import ti4.ai.agenda.GalacticThreatCost;
import ti4.ai.agenda.RiderPayoff;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.game.Game;
import ti4.game.Player;

/**
 * The agenda-local measure of agenda play, read from public state on every step of a self-play run (see
 * docs/ai/AGENDA_PLAN.md, milestone 2). Each revealed agenda, keyed by currentAgendaInfo, opens a record at reveal that
 * closes at the next reveal or when the agenda phase ends. Per agenda it records the round, agenda id, speaker, votes
 * per seat, riders, the outcome and every seat's VP and TG change over that window. It reports per seat:
 *
 * <ul>
 *   <li>realized agenda utility: AgendaUtility of the winning outcome at reveal minus the mean over all outcomes, in
 *       TG, each seat valued from its own view (its own secrets only) on one reference value table whatever its
 *       experiment flags (see AgendaUtilityProbe). Under Covert Legislation the hidden agenda the speaker drew is
 *       valued, never the "covert" prior; that id is read here for measurement only and never reaches the AI;
 *   <li>rider net value: for every rider the seat placed, the RiderPayoff (valued when the rider was first seen) if
 *       its outcome won, minus the average action card it cost;
 *   <li>agenda-sourced VP: the seat's VP change over the agenda's window;
 *   <li>harness defects: ties the referee broke instead of the speaker AI, ties a stalled speaker broke through its
 *       fallback, agendas whose utilities could not be taken, and agendas whose reveal-to-resolution wait went over
 *       budget (-Dai.selfplay.agenda.waitbudget minutes, default 30 virtual minutes).
 * </ul>
 */
final class AgendaTracker {

    private static final String AGENDA_PHASE_PREFIX = "agenda";
    private static final String RESOLVED_OUTCOME = "resolvedAgendaOutcome";
    private static final String COVERT_SUFFIX = "_CL_covert";
    private static final long WAIT_BUDGET_MILLIS = Long.getLong("ai.selfplay.agenda.waitbudget", 30L) * 60_000L;
    // Riders that are not action cards cost no card: Galactic Threat is a Nekro ability, the Keleres Rider a promissory
    // note. Predictions that are not named riders cost nothing either.
    private static final String RIDER_SUFFIX = "rider";
    private static final Set<String> NOT_ACTION_CARDS = Set.of("galactic threat rider", "keleres rider");

    record Rider(String faction, String rider, String outcome, double payoff, boolean won, double net) {}

    record SeatAgenda(
            String faction, Double realizedUtility, double riderNet, int riders, int votes, int vpDelta, int tgDelta) {}

    record Agenda(
            int round,
            int number,
            String info,
            String agenda,
            String speaker,
            String outcome,
            Map<String, String> votesBySeat,
            List<Rider> riders,
            Map<String, SeatAgenda> seats,
            long waitMillis,
            boolean overBudget,
            int refereeTieBreaks,
            int fallbackTieBreaks,
            boolean utilitiesMissing) {

        boolean resolved() {
            return !outcome.isBlank();
        }
    }

    record Summary(
            String game, List<Agenda> agendas, int refereeTieBreaks, int fallbackTieBreaks, int waitsOverBudget) {

        static final Summary NOTHING = new Summary("", List.of(), 0, 0, 0);

        int missingUtilities() {
            return (int) agendas.stream().filter(Agenda::utilitiesMissing).count();
        }

        int unmatchedUtilities() {
            return (int) agendas.stream()
                    .filter(Agenda::resolved)
                    .filter(agenda -> !agenda.utilitiesMissing())
                    .flatMap(agenda -> agenda.seats().values().stream())
                    .filter(seat -> seat.realizedUtility() == null)
                    .count();
        }

        // Per seat over the game: the sum of each agenda metric and the number of agendas the seat saw resolve.
        Map<String, double[]> perSeat() {
            Map<String, double[]> totals = new TreeMap<>();
            for (Agenda agenda : agendas) {
                if (!agenda.resolved()) continue;
                for (SeatAgenda seat : agenda.seats().values()) {
                    double[] values = totals.computeIfAbsent(seat.faction(), key -> new double[6]);
                    if (seat.realizedUtility() != null) {
                        values[0] += seat.realizedUtility();
                        values[5]++;
                    }
                    values[1] += seat.riderNet();
                    values[2] += seat.vpDelta();
                    values[3] += seat.tgDelta();
                    values[4]++;
                }
            }
            return totals;
        }
    }

    private static final class Open {
        final String info;
        final int round;
        final int number;
        final long openedAt;
        final String speaker;
        int refereeTieBreaks;
        int fallbackTieBreaks;
        final Map<String, Integer> vpAtReveal = new LinkedHashMap<>();
        final Map<String, Integer> tgAtReveal = new LinkedHashMap<>();
        final Map<String, List<String>> techsAtReveal = new LinkedHashMap<>();
        Map<String, Map<String, Double>> utilities = Map.of();
        final Map<String, Rider> riders = new LinkedHashMap<>();
        final Map<String, AgendaTrace.RiderSeen> ridersSeen = new LinkedHashMap<>();
        AgendaTrace.Window window;
        Map<String, String> votes = new LinkedHashMap<>();
        String agenda = "";
        String outcome = "";
        long resolvedAt = -1;

        Open(String info, int round, int number, long openedAt, String speaker) {
            this.info = info;
            this.round = round;
            this.number = number;
            this.openedAt = openedAt;
            this.speaker = speaker;
        }
    }

    private final List<Agenda> agendas = new ArrayList<>();
    private final Set<String> closedInfos = new HashSet<>();
    private Open open;
    private Summary finished;
    private int refereeTieBreaks;
    private int fallbackTieBreaks;
    private java.util.function.Supplier<List<SelfPlayEnvironment.TradeRecord>> trades = () -> null;
    private final AgendaTrace trace = new AgendaTrace(() -> trades.get());

    void readTradesFrom(java.util.function.Supplier<List<SelfPlayEnvironment.TradeRecord>> ledger) {
        trades = ledger;
    }

    void decided(ti4.ai.brain.AiTurnContext context, ti4.ai.brain.AiDecision decision) {
        trace.decided(context, decision);
    }

    // Keyed by the open agenda, never by reading the game: fetching the managed game on a press can reload it and
    // change the run.
    synchronized void pressed(long now, String faction, String customId, String outcome) {
        if (open != null) trace.pressed(open.info, now, faction, customId, outcome);
    }

    enum TieBreaker {
        REFEREE,
        FALLBACK
    }

    // A tie-break press that landed, made by someone other than the speaker AI's own agenda logic.
    synchronized void tieBroken(TieBreaker by) {
        if (by == TieBreaker.REFEREE) refereeTieBreaks++;
        else fallbackTieBreaks++;
        if (open == null) return;
        if (by == TieBreaker.REFEREE) open.refereeTieBreaks++;
        else open.fallbackTieBreaks++;
    }

    synchronized void observe(Game game, long now) {
        if (finished != null) return;
        String phase = game.getPhaseOfGame();
        boolean inAgenda = phase != null && phase.toLowerCase(Locale.ROOT).startsWith(AGENDA_PHASE_PREFIX);
        String info = game.getCurrentAgendaInfo();
        if (open != null && (!inAgenda || !open.info.equals(info))) close(game, now);
        if (!inAgenda || info == null || info.isBlank() || closedInfos.contains(info)) return;
        if (open == null) open = opened(game, info, now);
        if (open.utilities.isEmpty()) recordUtilities(game);
        if (!game.getCurrentAgendaVotes().isEmpty()) {
            open.votes = new LinkedHashMap<>(game.getCurrentAgendaVotes());
            recordRiders(game, now);
        }
        String resolved = game.getStoredValue(RESOLVED_OUTCOME);
        if (!resolved.isBlank() && open.outcome.isBlank()) {
            open.outcome = resolved;
            open.resolvedAt = now;
        }
        if (AgendaTrace.ENABLED && open.window != null) {
            try {
                open.window.observe(game, now, !open.outcome.isBlank());
            } catch (RuntimeException e) {
                System.out.println(AgendaTrace.PREFIX + " observe error " + e);
            }
        }
    }

    synchronized Summary summary(Game game, long now) {
        if (finished != null) return finished;
        if (open != null) close(game, now);
        int overBudget = (int) agendas.stream().filter(Agenda::overBudget).count();
        finished = new Summary(game.getName(), List.copyOf(agendas), refereeTieBreaks, fallbackTieBreaks, overBudget);
        return finished;
    }

    private Open opened(Game game, String info, long now) {
        Player speaker = game.getPlayer(game.getSpeakerUserID());
        Open opened =
                new Open(info, game.getRound(), agendas.size() + 1, now, speaker == null ? "-" : speaker.getFaction());
        for (Player seat : game.getRealPlayers()) {
            opened.vpAtReveal.put(seat.getFaction(), seat.getTotalVictoryPoints());
            opened.tgAtReveal.put(seat.getFaction(), seat.getTg());
            opened.techsAtReveal.put(seat.getFaction(), List.copyOf(seat.getTechs()));
        }
        if (AgendaTrace.ENABLED) {
            try {
                opened.window = trace.open(game, info, now);
            } catch (RuntimeException e) {
                System.out.println(AgendaTrace.PREFIX + " open error " + e);
            }
        }
        return opened;
    }

    // All or nothing: if any seat gets no values the agenda is retried on the next step, and if it never succeeds the
    // agenda is counted as a missing-utilities defect rather than as seats without a matching outcome.
    private void recordUtilities(Game game) {
        String agenda = valuedAgenda(game, open.info);
        open.agenda = agenda;
        Map<String, Map<String, Double>> taken = new LinkedHashMap<>();
        for (Player seat : game.getRealPlayers()) {
            Map<String, Double> values = AgendaUtilityProbe.utilities(game, seat, agenda);
            if (values.isEmpty()) return;
            taken.put(seat.getFaction(), values);
        }
        open.utilities = taken;
    }

    // The agenda whose effect will resolve: under Covert Legislation that is the hidden card the speaker drew, which
    // stays on top of the agenda deck, marked as sent to the speaker's hand, while the table votes.
    static String valuedAgenda(Game game, String info) {
        String agenda = AgendaUtilityProbe.currentAgenda(game);
        if (info == null || !info.endsWith(COVERT_SUFFIX) || game.getAgendas().isEmpty()) return agenda;
        String top = game.lookAtTopAgenda(0);
        return game.getSentAgendas().containsKey(top) ? top : agenda;
    }

    private void recordRiders(Game game, long now) {
        for (Map.Entry<String, String> entry : open.votes.entrySet()) {
            for (String piece : entry.getValue().split(";")) {
                int split = piece.indexOf('_');
                if (split <= 0) continue;
                String what = piece.substring(split + 1);
                if (isNumber(what)) continue;
                String key = piece + "|" + entry.getKey();
                if (open.riders.containsKey(key)) continue;
                Player owner = game.getPlayerFromColorOrFaction(piece.substring(0, split));
                if (owner == null) continue;
                double payoff = RiderPayoff.of(game, owner, what);
                open.riders.put(key, new Rider(owner.getFaction(), what, entry.getKey(), payoff, false, 0));
                if (AgendaTrace.ENABLED)
                    open.ridersSeen.put(key, riderSeen(game, owner, what, entry.getKey(), payoff, now));
            }
        }
    }

    // The rider as first seen: its owner's own forecast chance of the outcome and how many seats had voted by then.
    private AgendaTrace.RiderSeen riderSeen(
            Game game, Player owner, String rider, String outcome, double payoff, long now) {
        double chance;
        double threatWorth = Double.NaN;
        try {
            chance = ti4.ai.agenda.BallotProbe.riderChance(game, owner, outcome);
            if (isGalacticThreat(rider)) threatWorth = ti4.ai.agenda.BallotProbe.threatWorth(game, owner, outcome);
        } catch (RuntimeException e) {
            chance = Double.NaN;
        }
        int ballots = votesBySeat(game, open.votes).size();
        return new AgendaTrace.RiderSeen(
                owner.getFaction(),
                rider,
                outcome,
                payoff,
                chance,
                threatWorth,
                now,
                ballots,
                AgendaTrace.windowOf(game));
    }

    private void close(Game game, long now) {
        Open closing = open;
        open = null;
        closedInfos.add(closing.info);
        String winner = winnerKey(game, closing);
        List<Rider> riders = new ArrayList<>();
        Map<String, double[]> riderBySeat = new LinkedHashMap<>();
        for (Rider placed : closing.riders.values()) {
            boolean won = !closing.outcome.isBlank() && placed.outcome().equalsIgnoreCase(closing.outcome);
            double payoff = placed.payoff();
            if (isGalacticThreat(placed.rider())) {
                won = won && votedByAnotherSeat(game, closing.votes, placed.outcome(), placed.faction());
                payoff = techsGainedWorth(game, closing, placed.faction());
            }
            double cost = cardCost(placed.rider());
            double net = (won ? payoff : 0) - cost;
            riders.add(new Rider(placed.faction(), placed.rider(), placed.outcome(), payoff, won, net));
            double[] seat = riderBySeat.computeIfAbsent(placed.faction(), key -> new double[2]);
            seat[0] += net;
            seat[1]++;
        }
        Map<String, String> votesBySeat = votesBySeat(game, closing.votes);
        Map<String, SeatAgenda> seats = new LinkedHashMap<>();
        for (Player seat : game.getRealPlayers()) {
            String faction = seat.getFaction();
            double[] rider = riderBySeat.getOrDefault(faction, new double[2]);
            seats.put(
                    faction,
                    new SeatAgenda(
                            faction,
                            realizedUtility(closing.utilities.get(faction), winner),
                            rider[0],
                            (int) rider[1],
                            votesCast(game, closing.votes, seat),
                            seat.getTotalVictoryPoints() - closing.vpAtReveal.getOrDefault(faction, 0),
                            seat.getTg() - closing.tgAtReveal.getOrDefault(faction, 0)));
        }
        long wait = (closing.resolvedAt < 0 ? now : closing.resolvedAt) - closing.openedAt;
        agendas.add(new Agenda(
                closing.round,
                closing.number,
                closing.info,
                closing.agenda,
                closing.speaker,
                closing.outcome,
                votesBySeat,
                riders,
                seats,
                wait,
                wait > WAIT_BUDGET_MILLIS,
                closing.refereeTieBreaks,
                closing.fallbackTieBreaks,
                closing.utilities.isEmpty()));
        printTrace(game, closing, agendas.getLast());
    }

    private static void printTrace(Game game, Open closing, Agenda agenda) {
        if (!AgendaTrace.ENABLED || closing.window == null) return;
        try {
            System.out.println(closing.window.close(
                    game, game.getName(), agenda, List.copyOf(closing.ridersSeen.values()), closing.utilities));
        } catch (RuntimeException e) {
            System.out.println(AgendaTrace.PREFIX + " " + game.getName() + " #" + closing.number + " error " + e);
            e.printStackTrace(System.out);
        }
    }

    // Galactic Threat pays only through the winning voters (AgendaHelper.getWinningVoters): an outcome the speaker
    // picks
    // with no votes on it gives Nekro nothing, and what it does give is the technology Nekro actually copied.
    static boolean isGalacticThreat(String rider) {
        return GalacticThreatCost.RIDER.equalsIgnoreCase(rider.trim());
    }

    static boolean votedByAnotherSeat(Game game, Map<String, String> votes, String outcome, String owner) {
        for (Map.Entry<String, String> entry : votes.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(outcome)) continue;
            for (String piece : entry.getValue().split(";")) {
                int split = piece.indexOf('_');
                if (split <= 0 || !isNumber(piece.substring(split + 1))) continue;
                if (Integer.parseInt(piece.substring(split + 1)) <= 0) continue;
                Player voter = game.getPlayerFromColorOrFaction(piece.substring(0, split));
                if (voter != null && !voter.getFaction().equals(owner)) return true;
            }
        }
        return false;
    }

    private static double techsGainedWorth(Game game, Open closing, String faction) {
        Player owner = game.getPlayerFromColorOrFaction(faction);
        if (owner == null) return 0;
        List<String> gained = new ArrayList<>(owner.getTechs());
        gained.removeAll(closing.techsAtReveal.getOrDefault(faction, List.of()));
        return gained.stream().mapToDouble(CopiedTechPolicy::copiedWorth).sum();
    }

    private static double cardCost(String rider) {
        String name = rider.toLowerCase(Locale.ROOT).trim();
        if (!name.endsWith(RIDER_SUFFIX) || NOT_ACTION_CARDS.contains(name)) return 0;
        return ActionCardPrior.AVERAGE_CARD;
    }

    // The resolved outcome as one of the outcome keys the utilities were taken for: elections may resolve to a color.
    private static String winnerKey(Game game, Open closing) {
        if (closing.outcome.isBlank() || closing.utilities.isEmpty()) return "";
        Set<String> keys = closing.utilities.values().iterator().next().keySet();
        for (String key : keys) if (key.equalsIgnoreCase(closing.outcome)) return key;
        Player elected = game.getPlayerFromColorOrFaction(closing.outcome);
        if (elected != null && keys.contains(elected.getFaction())) return elected.getFaction();
        return "";
    }

    private static Double realizedUtility(Map<String, Double> utilities, String winner) {
        if (utilities == null || utilities.isEmpty() || winner.isBlank() || !utilities.containsKey(winner)) return null;
        double mean = utilities.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        return utilities.get(winner) - mean;
    }

    private static Map<String, String> votesBySeat(Game game, Map<String, String> votes) {
        Map<String, String> bySeat = new TreeMap<>();
        for (Map.Entry<String, String> entry : votes.entrySet()) {
            for (String piece : entry.getValue().split(";")) {
                int split = piece.indexOf('_');
                if (split <= 0 || !isNumber(piece.substring(split + 1))) continue;
                Player voter = game.getPlayerFromColorOrFaction(piece.substring(0, split));
                String name = voter == null ? piece.substring(0, split) : voter.getFaction();
                bySeat.merge(name, entry.getKey() + " " + piece.substring(split + 1), (a, b) -> a + "+" + b);
            }
        }
        return bySeat;
    }

    private static int votesCast(Game game, Map<String, String> votes, Player seat) {
        int total = 0;
        for (String entry : votes.values()) {
            for (String piece : entry.split(";")) {
                int split = piece.indexOf('_');
                if (split <= 0 || !isNumber(piece.substring(split + 1))) continue;
                if (game.getPlayerFromColorOrFaction(piece.substring(0, split)) == seat)
                    total += Integer.parseInt(piece.substring(split + 1));
            }
        }
        return total;
    }

    private static boolean isNumber(String text) {
        return !text.isEmpty() && text.length() < 9 && text.chars().allMatch(Character::isDigit);
    }

    // One AGENDA line per agenda and one per seat, for one game.
    static List<String> lines(Summary summary) {
        List<String> lines = new ArrayList<>();
        for (Agenda agenda : summary.agendas()) {
            StringBuilder seats = new StringBuilder();
            agenda.seats()
                    .values()
                    .forEach(seat -> seats.append(String.format(
                            " %s[u %s r %+.1f vp %+d tg %+d v %d]",
                            seat.faction(),
                            seat.realizedUtility() == null ? "-" : String.format("%+.2f", seat.realizedUtility()),
                            seat.riderNet(),
                            seat.vpDelta(),
                            seat.tgDelta(),
                            seat.votes())));
            List<String> riders = agenda.riders().stream()
                    .map(rider -> String.format(
                            "%s %s on %s %s %+.1f",
                            rider.faction(), rider.rider(), rider.outcome(), rider.won() ? "won" : "lost", rider.net()))
                    .toList();
            lines.add(String.format(
                    "AGENDA %s r%d #%d %s speaker %s outcome %s wait %.1fm%s%s%s%s votes %s riders %s |%s",
                    summary.game(),
                    agenda.round(),
                    agenda.number(),
                    agenda.agenda().isBlank() ? agenda.info() : agenda.agenda(),
                    agenda.speaker(),
                    agenda.resolved() ? agenda.outcome() : "UNRESOLVED",
                    agenda.waitMillis() / 60_000.0,
                    agenda.overBudget() ? " OVER-BUDGET" : "",
                    agenda.refereeTieBreaks() > 0 ? " REFEREE-TIE " + agenda.refereeTieBreaks() : "",
                    agenda.fallbackTieBreaks() > 0 ? " FALLBACK-TIE " + agenda.fallbackTieBreaks() : "",
                    agenda.utilitiesMissing() ? " NO-UTILITIES" : "",
                    agenda.votesBySeat(),
                    riders,
                    seats));
        }
        summary.perSeat()
                .forEach((faction, values) -> lines.add(String.format(
                        "AGENDA SEAT %s %-8s agendas %.0f, utility %+.2f per agenda, rider net %+.2f, agenda VP %+.0f,"
                                + " agenda TG %+.0f",
                        summary.game(),
                        faction,
                        values[4],
                        values[5] == 0 ? 0 : values[0] / values[5],
                        values[1],
                        values[2],
                        values[3])));
        lines.add(String.format(
                "AGENDA GAME %s: %d agendas (%d resolved), referee-broken ties %d, fallback-broken ties %d, waits over"
                        + " budget %d, agendas without utilities %d, seat utilities without a matching outcome %d",
                summary.game(),
                summary.agendas().size(),
                summary.agendas().stream().filter(Agenda::resolved).count(),
                summary.refereeTieBreaks(),
                summary.fallbackTieBreaks(),
                summary.waitsOverBudget(),
                summary.missingUtilities(),
                summary.unmatchedUtilities()));
        return lines;
    }

    // The batch report: totals per faction and over the table, then, in an A/B batch, treatment minus control per
    // agenda metric with the standard error over seed pairs.
    static void report(List<SelfPlayGame.Outcome> outcomes) {
        List<Summary> summaries =
                outcomes.stream().map(SelfPlayGame.Outcome::agendas).toList();
        Map<String, double[]> byFaction = new TreeMap<>();
        List<Double> perGameUtility = new ArrayList<>();
        int agendaCount = 0;
        int resolved = 0;
        int tieBreaks = 0;
        int fallbackTieBreaks = 0;
        int missing = 0;
        int overBudget = 0;
        int unmatched = 0;
        long waitMillis = 0;
        for (Summary summary : summaries) {
            agendaCount += summary.agendas().size();
            tieBreaks += summary.refereeTieBreaks();
            fallbackTieBreaks += summary.fallbackTieBreaks();
            missing += summary.missingUtilities();
            overBudget += summary.waitsOverBudget();
            unmatched += summary.unmatchedUtilities();
            for (Agenda agenda : summary.agendas()) {
                if (!agenda.resolved()) continue;
                resolved++;
                waitMillis += agenda.waitMillis();
            }
            double gameUtility = 0;
            double gameCount = 0;
            for (Map.Entry<String, double[]> seat : summary.perSeat().entrySet()) {
                double[] totals = byFaction.computeIfAbsent(seat.getKey(), key -> new double[6]);
                for (int i = 0; i < totals.length; i++) totals[i] += seat.getValue()[i];
                gameUtility += seat.getValue()[0];
                gameCount += seat.getValue()[5];
            }
            if (gameCount > 0) perGameUtility.add(gameUtility / gameCount);
        }
        double[] table = new double[6];
        byFaction.forEach((faction, totals) -> {
            for (int i = 0; i < table.length; i++) table[i] += totals[i];
            System.out.println(totalsLine("AGENDA FACTION " + String.format("%-8s", faction), totals));
        });
        System.out.println(
                totalsLine("AGENDA TOTAL", table) + " | utility per game " + AbSummary.meanAndError(perGameUtility));
        System.out.printf(
                "AGENDA DEFECTS over %d games: %d agendas (%d resolved, mean wait %.1fm), referee-broken ties %d,"
                        + " fallback-broken ties %d, waits over budget %d, agendas without utilities %d, seat utilities"
                        + " without a matching outcome %d%n",
                summaries.size(),
                agendaCount,
                resolved,
                resolved == 0 ? 0 : waitMillis / 60_000.0 / resolved,
                tieBreaks,
                fallbackTieBreaks,
                overBudget,
                missing,
                unmatched);
        if (SelfPlayGame.AB.isPresent()) reportAb(outcomes);
    }

    private static String totalsLine(String label, double[] totals) {
        double seatAgendas = Math.max(1, totals[4]);
        return String.format(
                "%s: %.0f seat-agendas, utility %+.3f per agenda, rider net %+.3f, agenda VP %+.3f, agenda TG %+.3f"
                        + " per seat-agenda",
                label,
                totals[4],
                totals[5] == 0 ? 0 : totals[0] / totals[5],
                totals[1] / seatAgendas,
                totals[2] / seatAgendas,
                totals[3] / seatAgendas);
    }

    private static final String[] METRICS = {"utility", "rider net", "agenda VP", "agenda TG"};

    // Per game, the treatment seats' mean per seat-agenda minus the control seats', averaged within each seed pair.
    private static void reportAb(List<SelfPlayGame.Outcome> outcomes) {
        Map<Long, List<double[]>> pairs = new TreeMap<>();
        for (SelfPlayGame.Outcome outcome : outcomes) {
            double[] difference = abDifference(outcome);
            if (difference == null) continue;
            System.out.printf(
                    "AGENDA ABGAME slot=%d seed=%d utility=%+.3f riders=%+.3f vp=%+.3f tg=%+.3f%n",
                    outcome.slot(),
                    SelfPlayGame.seedFor(outcome.slot()),
                    difference[0],
                    difference[1],
                    difference[2],
                    difference[3]);
            pairs.computeIfAbsent(SelfPlayGame.seedFor(outcome.slot()), seed -> new ArrayList<>())
                    .add(difference);
        }
        List<double[]> complete = pairs.values().stream()
                .filter(pair -> pair.size() == 2)
                .map(pair -> {
                    double[] mean = new double[METRICS.length];
                    for (int i = 0; i < mean.length; i++) mean[i] = (pair.get(0)[i] + pair.get(1)[i]) / 2;
                    return mean;
                })
                .toList();
        StringBuilder line = new StringBuilder(String.format(
                "AGENDA A/B %s: %d complete pairs, treatment minus control per seat-agenda:",
                SelfPlayGame.AB.get().isEmpty() ? "none (A/A)" : SelfPlayGame.AB.get(), complete.size()));
        for (int i = 0; i < METRICS.length; i++) {
            int metric = i;
            line.append(String.format(
                    " %s %s;",
                    METRICS[i],
                    AbSummary.meanAndError(
                            complete.stream().map(values -> values[metric]).toList())));
        }
        System.out.println(line);
    }

    private static double[] abDifference(SelfPlayGame.Outcome outcome) {
        Map<String, double[]> perSeat = outcome.agendas().perSeat();
        double[] treatment = new double[6];
        double[] control = new double[6];
        for (Map.Entry<String, double[]> seat : perSeat.entrySet()) {
            double[] side = outcome.controlSeats().contains(seat.getKey()) ? control : treatment;
            for (int i = 0; i < side.length; i++) side[i] += seat.getValue()[i];
        }
        if (treatment[4] == 0 || control[4] == 0) return null;
        return new double[] {
            perAgenda(treatment[0], treatment[5]) - perAgenda(control[0], control[5]),
            treatment[1] / treatment[4] - control[1] / control[4],
            treatment[2] / treatment[4] - control[2] / control[4],
            treatment[3] / treatment[4] - control[3] / control[4]
        };
    }

    private static double perAgenda(double total, double count) {
        return count == 0 ? 0 : total / count;
    }
}
