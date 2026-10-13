package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretPhase;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.info.ListPlayerInfoService;

// Test-only "missed scoring" diagnostic for the round-one harness. It follows what each seat spends during a round
// (readied planet value, trade goods and command tokens, attributed to the purpose of the press that spent them), and
// at each status phase records, for every revealed public objective and held secret, whether the seat met it before
// scoring and whether it scored it. A seat that could not pay for a spend objective, but could have with what it spent
// that round, counts as crowded out by those spends.
final class ScoringDiagnostic {

    static final int ALMOST = 2;
    private static final Pattern PAYMENT_PREFIX =
            Pattern.compile("^(exhaust \\S+ for |spend a trade good for |finish paying for |pay for )");
    private static final Pattern CUT = Pattern.compile(" \\(| at | to | in | from | on | with |: |, ");

    record Spend(int resources, int influence, int tradeGoods, int tokens) {
        Spend plus(Spend other) {
            return new Spend(
                    resources + other.resources,
                    influence + other.influence,
                    tradeGoods + other.tradeGoods,
                    tokens + other.tokens);
        }

        boolean isNone() {
            return resources == 0 && influence == 0 && tradeGoods == 0 && tokens == 0;
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            if (resources > 0) parts.add(resources + "r");
            if (influence > 0) parts.add(influence + "i");
            if (tradeGoods > 0) parts.add(tradeGoods + "tg");
            if (tokens > 0) parts.add(tokens + "cc");
            return String.join("/", parts);
        }
    }

    // One objective as a seat stood before status-phase scoring.
    record Standing(String id, boolean secret, boolean spend, int points, boolean met, int shortBy, String detail) {}

    record SeatRound(
            String faction,
            int round,
            List<Standing> standings,
            List<String> scoredPublics,
            List<String> scoredSecrets,
            List<String> scoredInActionPhase,
            Map<String, Spend> spends,
            List<String> crowdedOut,
            List<String> brokeAffordable,
            List<String> cards,
            String board) {

        Optional<Standing> bestMissedPublic() {
            if (!scoredPublics.isEmpty()) return Optional.empty();
            return standings.stream()
                    .filter(standing -> !standing.secret() && standing.met())
                    .findFirst();
        }

        Optional<Standing> bestMissedSecret() {
            if (!scoredSecrets.isEmpty()) return Optional.empty();
            return standings.stream()
                    .filter(standing -> standing.secret() && standing.met())
                    .findFirst();
        }
    }

    private record Snapshot(Wallet wallet, Set<String> readied) {
        static Snapshot of(Game game, Player seat) {
            return new Snapshot(Wallet.of(game, seat), new HashSet<>(seat.getReadiedPlanets()));
        }

        Spend spentSince(Snapshot earlier) {
            return new Spend(
                    Math.max(0, earlier.wallet.resources() - wallet.resources()),
                    Math.max(0, earlier.wallet.influence() - wallet.influence()),
                    Math.max(0, earlier.wallet.tradeGoods() - wallet.tradeGoods()),
                    Math.max(0, earlier.wallet.tokens() - wallet.tokens()));
        }
    }

    private final Map<String, Snapshot> lastSnapshot = new HashMap<>();
    private final Map<String, String> lastPurpose = new HashMap<>();
    private final Map<Integer, Map<String, Map<String, Spend>>> spends = new TreeMap<>();
    private final Map<Integer, Map<String, Set<String>>> scoredBeforeStatus = new HashMap<>();
    private final Map<Integer, Map<String, Set<String>>> scoredAtRoundStart = new HashMap<>();
    private final Map<Integer, Map<String, List<Standing>>> standings = new HashMap<>();
    private final Map<Integer, Map<String, List<String>>> crowdedOut = new HashMap<>();
    private final Map<Integer, Map<String, Map<String, Spend>>> spendsAtStatus = new HashMap<>();
    private final Map<Integer, Map<String, String>> statusBoard = new HashMap<>();
    private final Map<Integer, Map<String, List<String>>> cards = new HashMap<>();
    // Planets each seat exhausted while it still held them, by round: what it would have had ready at status time.
    private final Map<Integer, Map<String, Set<String>>> exhausted = new HashMap<>();
    // Spends that took an affordable, unscored spend objective out of reach ("objective <- purpose"), by round.
    private final Map<Integer, Map<String, List<String>>> broke = new HashMap<>();
    private final Set<Integer> closedRounds = new HashSet<>();
    final List<SeatRound> results = new ArrayList<>();

    void onDecision(AiTurnContext context, AiDecision decision) {
        Game game = context.game();
        Player seat = context.seat();
        if (game == null || seat == null) return;
        settle(game, seat);
        if (decision instanceof AiDecision.Press press) lastPurpose.put(seat.getFaction(), purpose(press.reason()));
    }

    // Called by the harness on every step with the live game.
    void observe(Game game) {
        int round = game.getRound();
        String phase = game.getPhaseOfGame();
        if ("action".equalsIgnoreCase(phase) && !scoredAtRoundStart.containsKey(round)) {
            Map<String, Set<String>> start = new HashMap<>();
            Map<String, List<String>> held = new HashMap<>();
            for (Player seat : game.getRealPlayers()) {
                start.put(seat.getFaction(), scored(game, seat));
                held.put(
                        seat.getFaction(),
                        seat.getSCs().stream()
                                .map(initiative -> initiative + " "
                                        + ti4.ai.brain.StrategyCard.of(game, initiative)
                                                .name()
                                                .toLowerCase())
                                .toList());
            }
            scoredAtRoundStart.put(round, start);
            cards.put(round, held);
        }
        boolean scoring = "statusScoring".equalsIgnoreCase(phase);
        for (Integer open : new ArrayList<>(standings.keySet())) {
            if (!closedRounds.contains(open) && (open < round || !scoring)) afterScoring(game, open);
        }
        if (scoring && !standings.containsKey(round)) beforeScoring(game, round);
    }

    // A game that ends during its last status phase still closes that round.
    void finish(Game game) {
        for (Integer round : standings.keySet()) {
            if (!closedRounds.contains(round)) afterScoring(game, round);
        }
    }

    private void settle(Game game, Player seat) {
        String faction = seat.getFaction();
        Snapshot now = Snapshot.of(game, seat);
        Snapshot before = lastSnapshot.put(faction, now);
        if (before == null || "statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) return;
        Spend spent = now.spentSince(before);
        if (spent.isNone()) return;
        int round = game.getRound();
        String purpose = lastPurpose.getOrDefault(faction, "(before any press)");
        spends.computeIfAbsent(round, key -> new TreeMap<>())
                .computeIfAbsent(faction, key -> new TreeMap<>())
                .merge(purpose, spent, Spend::plus);
        Set<String> used = exhausted
                .computeIfAbsent(round, key -> new HashMap<>())
                .computeIfAbsent(faction, key -> new HashSet<>());
        for (String planet : before.readied()) {
            if (!now.readied().contains(planet) && seat.getPlanets().contains(planet)) used.add(planet);
        }
        if (purpose.contains("objective") || !"action".equalsIgnoreCase(game.getPhaseOfGame())) return;
        for (String objective : game.getRevealedPublicObjectives().keySet()) {
            Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
            if (cost.isEmpty() || ObjectivePolicy.hasScored(game, seat, objective)) continue;
            if (before.wallet().canPay(cost.get()) && !now.wallet().canPay(cost.get())) {
                broke.computeIfAbsent(round, key -> new HashMap<>())
                        .computeIfAbsent(faction, key -> new ArrayList<>())
                        .add(objective + " <- " + purpose);
            }
        }
    }

    private void beforeScoring(Game game, int round) {
        Map<String, List<Standing>> byFaction = new HashMap<>();
        Map<String, Set<String>> scoredSoFar = new HashMap<>();
        Map<String, List<String>> crowded = new HashMap<>();
        for (Player seat : game.getRealPlayers()) {
            settle(game, seat);
            String faction = seat.getFaction();
            scoredSoFar.put(faction, scored(game, seat));
            Wallet wallet = Wallet.of(game, seat);
            Wallet unspent = unspent(game, seat, wallet, round);
            List<Standing> list = new ArrayList<>();
            List<String> crowdedList = new ArrayList<>();
            for (String objective : game.getRevealedPublicObjectives().keySet()) {
                if (ObjectivePolicy.hasScored(game, seat, objective)) continue;
                if (ti4.image.Mapper.getPublicObjective(objective) == null) continue;
                Standing standing = publicStanding(game, seat, wallet, objective);
                list.add(standing);
                Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
                if (cost.isPresent() && !standing.met() && unspent.canPay(cost.get())) {
                    crowdedList.add(objective);
                }
            }
            for (String secret : seat.getSecretsUnscored().keySet()) list.add(secretStanding(game, seat, secret));
            byFaction.put(faction, list);
            statusBoard
                    .computeIfAbsent(round, key -> new HashMap<>())
                    .put(
                            faction,
                            "tokens t" + seat.getTacticalCC() + " s" + seat.getStrategicCC() + " f" + seat.getFleetCC()
                                    + " tg" + seat.getTg() + " comm " + seat.getCommodities() + " exhausted "
                                    + new java.util.TreeSet<>(exhausted
                                            .getOrDefault(round, Map.of())
                                            .getOrDefault(faction, Set.of())));
            crowded.put(faction, crowdedList);
        }
        standings.put(round, byFaction);
        scoredBeforeStatus.put(round, scoredSoFar);
        crowdedOut.put(round, crowded);
        Map<String, Map<String, Spend>> copy = new HashMap<>();
        spends.getOrDefault(round, Map.of()).forEach((faction, map) -> copy.put(faction, new TreeMap<>(map)));
        spendsAtStatus.put(round, copy);
    }

    private void afterScoring(Game game, int round) {
        closedRounds.add(round);
        for (Player seat : game.getRealPlayers()) {
            String faction = seat.getFaction();
            Set<String> before = scoredBeforeStatus.get(round).getOrDefault(faction, Set.of());
            Set<String> atStart =
                    scoredAtRoundStart.getOrDefault(round, Map.of()).getOrDefault(faction, Set.of());
            List<String> publics = new ArrayList<>();
            List<String> secrets = new ArrayList<>();
            for (String id : scored(game, seat)) {
                if (before.contains(id)) continue;
                if (seat.getSecretsScored().containsKey(id)) secrets.add(id);
                else publics.add(id);
            }
            List<String> inAction =
                    before.stream().filter(id -> !atStart.contains(id)).toList();
            results.add(new SeatRound(
                    faction,
                    round,
                    standings.get(round).getOrDefault(faction, List.of()),
                    publics,
                    secrets,
                    inAction,
                    spendsAtStatus.getOrDefault(round, Map.of()).getOrDefault(faction, Map.of()),
                    crowdedOut.get(round).getOrDefault(faction, List.of()),
                    broke.getOrDefault(round, Map.of()).getOrDefault(faction, List.of()),
                    cards.getOrDefault(round, Map.of()).getOrDefault(faction, List.of()),
                    statusBoard.getOrDefault(round, Map.of()).getOrDefault(faction, "")));
        }
    }

    // What the seat would have held at status time had it spent nothing this round: every planet ready, and the
    // trade goods and tokens it spent back in hand.
    private Wallet unspent(Game game, Player seat, Wallet wallet, int round) {
        Spend spent = spends.getOrDefault(round, Map.of()).getOrDefault(seat.getFaction(), Map.of()).values().stream()
                .reduce(new Spend(0, 0, 0, 0), Spend::plus);
        Wallet all = wallet.withPlanets(
                game, exhausted.getOrDefault(round, Map.of()).getOrDefault(seat.getFaction(), Set.of()));
        return new Wallet(
                all.planets(),
                wallet.tradeGoods() + spent.tradeGoods(),
                wallet.tacticTokens() + spent.tokens(),
                wallet.strategyTokens(),
                wallet.tradeGoodValue());
    }

    private static Standing publicStanding(Game game, Player seat, Wallet wallet, String objective) {
        int points = ObjectivePolicy.victoryPoints(objective);
        boolean met = ObjectivePolicy.qualifies(game, seat, wallet, objective);
        Optional<SpendCost> cost = ObjectiveCatalog.spendCost(objective);
        if (cost.isPresent()) {
            int short_ = shortfall(wallet, cost.get());
            String detail = "have " + wallet.resources() + "r/" + wallet.influence() + "i/" + wallet.tradeGoods()
                    + "tg/" + wallet.tokens() + "cc need " + describe(cost.get());
            return new Standing(objective, false, true, points, met, met ? 0 : short_, detail);
        }
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        int progress = ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat);
        return new Standing(
                objective,
                false,
                false,
                points,
                met,
                met ? 0 : Math.max(0, threshold - progress),
                progress + "/" + threshold);
    }

    private static Standing secretStanding(Game game, Player seat, String secret) {
        SecretPhase phase = SecretPhase.of(secret);
        int threshold = ListPlayerInfoService.getObjectiveThreshold(secret, game);
        int progress = "ans".equals(secret)
                ? SecretValue.ownFactionTechnologies(seat)
                : ListPlayerInfoService.getPlayerProgressOnObjective(secret, game, seat);
        boolean met = phase == SecretPhase.STATUS && threshold > 0 && progress >= threshold;
        int shortBy = phase != SecretPhase.STATUS || threshold <= 0 ? 99 : Math.max(0, threshold - progress);
        return new Standing(
                secret, true, false, 1, met, shortBy, phase.name().toLowerCase() + " " + progress + "/" + threshold);
    }

    // How many trade goods (or tokens, for a token cost) the seat lacked to pay the cost.
    static int shortfall(Wallet wallet, SpendCost cost) {
        int tokensShort = Math.max(0, cost.tokens() - wallet.tokens());
        for (int extra = 0; extra <= 20; extra++) {
            Wallet richer = new Wallet(
                    wallet.planets(),
                    wallet.tradeGoods() + extra,
                    wallet.tacticTokens() + tokensShort,
                    wallet.strategyTokens(),
                    wallet.tradeGoodValue());
            if (richer.canPay(cost)) return extra + tokensShort;
        }
        return 99;
    }

    private static String describe(SpendCost cost) {
        return new Spend(cost.resources(), cost.influence(), cost.tradeGoods(), cost.tokens()).toString();
    }

    private static Set<String> scored(Game game, Player seat) {
        Set<String> scored = new HashSet<>();
        game.getScoredPublicObjectives().forEach((objective, players) -> {
            if (players.contains(seat.getUserID())) scored.add(objective);
        });
        scored.addAll(seat.getSecretsScored().keySet());
        return scored;
    }

    static String purpose(String reason) {
        String text = PAYMENT_PREFIX.matcher(reason).replaceFirst("");
        text = text.replaceAll("[0-9]+", "#");
        String[] cut = CUT.split(text, 2);
        String head = cut.length == 0 ? text : cut[0];
        String[] words = head.trim().split("\\s+");
        return String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, 6)));
    }

    // ---- reporting across games ----

    static void report(List<SeatRound> all) {
        Map<Integer, List<SeatRound>> byRound =
                all.stream().collect(Collectors.groupingBy(SeatRound::round, TreeMap::new, Collectors.toList()));
        byRound.forEach((round, seats) -> reportRound(round, seats));
    }

    private static void reportRound(int round, List<SeatRound> seats) {
        int n = seats.size();
        long scoredPublic =
                seats.stream().filter(seat -> !seat.scoredPublics().isEmpty()).count();
        long scoredSecret =
                seats.stream().filter(seat -> !seat.scoredSecrets().isEmpty()).count();
        long actionScores = seats.stream()
                .mapToLong(seat -> seat.scoredInActionPhase().size())
                .sum();
        long missedPublic = seats.stream()
                .filter(seat -> seat.bestMissedPublic().isPresent())
                .count();
        long missedSecret = seats.stream()
                .filter(seat -> seat.bestMissedSecret().isPresent())
                .count();
        long metPublic = seats.stream()
                .filter(seat -> seat.standings().stream().anyMatch(s -> !s.secret() && s.met()))
                .count();
        long metSecret = seats.stream()
                .filter(seat -> seat.standings().stream().anyMatch(s -> s.secret() && s.met()))
                .count();
        System.out.printf(
                "SCOREDIAG ROUND %d over %d seat-rounds: public met %d scored %d (met-not-scored %d); status secret met"
                        + " %d scored %d (met-not-scored %d); scored during action phase %d%n",
                round, n, metPublic, scoredPublic, missedPublic, metSecret, scoredSecret, missedSecret, actionScores);

        Map<String, int[]> publics = new TreeMap<>();
        for (SeatRound seat : seats) {
            for (Standing standing : seat.standings()) {
                if (standing.secret()) continue;
                int[] tally = publics.computeIfAbsent(standing.id(), key -> new int[5]);
                tally[0]++;
                if (standing.met()) tally[1]++;
                if (seat.scoredPublics().contains(standing.id())) tally[2]++;
                if (!standing.met() && standing.shortBy() > 0 && standing.shortBy() <= ALMOST) tally[3]++;
                if (seat.crowdedOut().contains(standing.id())) tally[4]++;
            }
        }
        publics.forEach((id, tally) -> System.out.printf(
                "SCOREDIAG   r%d public %-18s seats %3d met %3d scored %3d almost(<=%d short) %3d crowded-out %3d%n",
                round, id, tally[0], tally[1], tally[2], ALMOST, tally[3], tally[4]));

        Map<String, int[]> secrets = new TreeMap<>();
        for (SeatRound seat : seats) {
            for (Standing standing : seat.standings()) {
                if (!standing.secret()) continue;
                int[] tally = secrets.computeIfAbsent(standing.id(), key -> new int[4]);
                tally[0]++;
                if (standing.met()) tally[1]++;
                if (seat.scoredSecrets().contains(standing.id())) tally[2]++;
                if (!standing.met() && standing.shortBy() > 0 && standing.shortBy() <= ALMOST) tally[3]++;
            }
            for (String id : seat.scoredInActionPhase()) {
                if (seat.standings().stream().noneMatch(s -> s.id().equals(id)))
                    secrets.computeIfAbsent("(action) " + id, key -> new int[4])[2]++;
            }
        }
        secrets.forEach((id, tally) -> System.out.printf(
                "SCOREDIAG   r%d secret %-18s held %3d met %3d scored %3d almost %3d%n",
                round, id, tally[0], tally[1], tally[2], tally[3]));

        Map<String, Spend> spendTotals = new TreeMap<>();
        Map<String, Spend> crowdingSpends = new TreeMap<>();
        Map<String, Spend> almostSpends = new TreeMap<>();
        int crowdedSeats = 0;
        int almostSeats = 0;
        for (SeatRound seat : seats) {
            seat.spends().forEach((purpose, spend) -> spendTotals.merge(purpose, spend, Spend::plus));
            boolean noPublic =
                    seat.scoredPublics().isEmpty() && seat.standings().stream().noneMatch(s -> !s.secret() && s.met());
            if (noPublic && !seat.crowdedOut().isEmpty()) {
                crowdedSeats++;
                seat.spends().forEach((purpose, spend) -> crowdingSpends.merge(purpose, spend, Spend::plus));
            }
            boolean almostSpend = noPublic
                    && seat.standings().stream()
                            .anyMatch(s -> !s.secret() && s.spend() && s.shortBy() > 0 && s.shortBy() <= ALMOST);
            if (almostSpend) {
                almostSeats++;
                seat.spends().forEach((purpose, spend) -> almostSpends.merge(purpose, spend, Spend::plus));
            }
        }
        System.out.printf("SCOREDIAG   r%d spends (all seats): %s%n", round, top(spendTotals, 14));
        System.out.printf(
                "SCOREDIAG   r%d %d seats scored no public but could have paid a spend objective had they not spent:"
                        + " %s%n",
                round, crowdedSeats, top(crowdingSpends, 10));
        System.out.printf(
                "SCOREDIAG   r%d %d seats scored no public and were within %d of a spend objective; their spends: %s%n",
                round, almostSeats, ALMOST, top(almostSpends, 10));
        Map<String, Integer> breaks = new TreeMap<>();
        int brokeAndMissed = 0;
        for (SeatRound seat : seats) {
            boolean noPublic =
                    seat.scoredPublics().isEmpty() && seat.standings().stream().noneMatch(s -> !s.secret() && s.met());
            if (!noPublic || seat.brokeAffordable().isEmpty()) continue;
            brokeAndMissed++;
            seat.brokeAffordable().forEach(entry -> breaks.merge(entry, 1, Integer::sum));
        }
        System.out.printf(
                "SCOREDIAG   r%d %d seats scored no public after an action-phase spend took an affordable spend"
                        + " objective out of reach: %s%n",
                round, brokeAndMissed, breaks);

        Map<String, Integer> byCards = new TreeMap<>();
        Map<String, Integer> byCardsScored = new TreeMap<>();
        for (SeatRound seat : seats) {
            for (String card : seat.cards()) {
                byCards.merge(card, 1, Integer::sum);
                if (!seat.scoredPublics().isEmpty() || !seat.scoredSecrets().isEmpty())
                    byCardsScored.merge(card, 1, Integer::sum);
            }
        }
        System.out.printf(
                "SCOREDIAG   r%d scored anything at status, by initiative held: %s%n",
                round,
                byCards.entrySet().stream()
                        .map(entry -> entry.getKey() + ":" + byCardsScored.getOrDefault(entry.getKey(), 0) + "/"
                                + entry.getValue())
                        .collect(Collectors.joining(" ")));
    }

    private static String top(Map<String, Spend> spends, int limit) {
        return spends.entrySet().stream()
                .sorted((a, b) -> Integer.compare(weight(b.getValue()), weight(a.getValue())))
                .limit(limit)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; ", "{", "}"));
    }

    private static int weight(Spend spend) {
        return Math.max(spend.resources(), spend.influence()) + spend.tradeGoods() + 2 * spend.tokens();
    }

    static String line(SeatRound seat) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put(
                "objectives",
                seat.standings().stream()
                        .map(s -> s.id() + (s.met() ? "=MET" : "-" + s.shortBy()) + "(" + s.detail() + ")")
                        .collect(Collectors.joining(" ")));
        parts.put(
                "scored", seat.scoredPublics() + "+" + seat.scoredSecrets() + " action " + seat.scoredInActionPhase());
        if (!seat.crowdedOut().isEmpty()) parts.put("crowded", seat.crowdedOut().toString());
        if (!seat.brokeAffordable().isEmpty())
            parts.put("broke", seat.brokeAffordable().toString());
        parts.put("spent", top(seat.spends(), 8));
        return String.format(
                "SCOREDIAG g r%d %-8s cards %s %s | %s",
                seat.round(),
                seat.faction(),
                seat.cards(),
                seat.board(),
                parts.entrySet().stream()
                        .map(entry -> entry.getKey() + " " + entry.getValue())
                        .collect(Collectors.joining(" | ")));
    }
}
