package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Helper;

public final class AgendaForecast {

    private static final int SAMPLES = 256;
    private static final double TEMPERATURE = 3.0;
    private static final double INDIFFERENT_SPREAD = 1.0;
    private static final double ABSTAINS_WHEN_INDIFFERENT = 0.3;
    private static final String ASSASSINATED = "AssassinatedReps";
    private static final String XXCHA_COMMANDER = "xxchacommander";
    private static final String AGENDA_PHASE = "agenda";
    private static final String VOTING_PHASE = "agendavoting";
    private static final String END_PHASE = "agendaend";
    private static final AgendaForecast NONE =
            new AgendaForecast(null, new int[0], new int[0], new int[0][0], List.of());

    private record Rider(int owner, String title, int outcome, double payoff, double rivalStake) {}

    private record Frame(
            Game game,
            Player viewer,
            List<Player> voters,
            List<String> outcomes,
            double[][] base,
            double[][] draws,
            int speaker,
            boolean[] keepsVotesWithRider) {}

    private record Placed(int[] castByOutcome, int[] castByVoter, int[][] castBy, List<Rider> riders) {}

    private final Frame frame;
    private final int[] votes;
    private final int[] cast;
    private final int[][] castBy;
    private final List<Rider> riders;
    private double[][] utilities;
    private Map<String, Double> chances;
    private Map<String, Double> votedChances;

    private AgendaForecast(Frame frame, int[] votes, int[] cast, int[][] castBy, List<Rider> riders) {
        this.frame = frame;
        this.votes = votes;
        this.cast = cast;
        this.castBy = castBy;
        this.riders = riders;
    }

    public static AgendaForecast of(Game game, Player viewer) {
        try (Thinking.Span timing = Thinking.measure("agenda.forecast")) {
            if (!applies(game)) return NONE;
            List<String> outcomes = AgendaOutcomes.of(game);
            List<Player> voters = votingOrder(game);
            if (outcomes.isEmpty() || voters.isEmpty()) return NONE;
            String agenda = AgendaPolicy.currentAgenda(game);
            Placed placed = readPlaced(game, viewer, voters, outcomes);
            Frame frame = new Frame(
                    game,
                    viewer,
                    voters,
                    outcomes,
                    baseUtilities(game, viewer, voters, outcomes, agenda),
                    draws(game, viewer, voters.size()),
                    speakerIndex(game, voters),
                    keepsVotesWithRider(game, voters));
            return PromisedVotes.placeAccepted(
                    game,
                    viewer,
                    new AgendaForecast(
                            frame,
                            remainingVotes(game, frame, placed),
                            placed.castByOutcome(),
                            placed.castBy(),
                            placed.riders()));
        }
    }

    public boolean isEmpty() {
        return frame == null;
    }

    public Map<String, Double> chances() {
        if (isEmpty()) return Map.of();
        if (chances == null) simulate();
        return chances;
    }

    public double votedChance(String outcome) {
        if (isEmpty()) return 0;
        if (votedChances == null) simulate();
        return votedChances.getOrDefault(outcome, 0.0);
    }

    public java.util.Set<String> outcomes() {
        return chances().keySet();
    }

    public double chance(String outcome) {
        return chances().getOrDefault(outcome, 0.0);
    }

    public Optional<String> mostLikely() {
        return chances().entrySet().stream()
                .max(Map.Entry.<String, Double>comparingByValue())
                .map(Map.Entry::getKey);
    }

    public double expected(ToDoubleFunction<String> value) {
        return chances().entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * value.applyAsDouble(entry.getKey()))
                .sum();
    }

    public boolean hasRivalImperialRider(Player seat) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(seat);
        return riders.stream()
                .anyMatch(rider ->
                        rider.owner() != voter && RiderPayoff.isImperial(rider.title()) && rider.outcome() >= 0);
    }

    public Optional<String> riderOutcome(Player owner, String title) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(owner);
        return riders.stream()
                .filter(rider -> rider.owner() == voter && rider.title().equals(title) && rider.outcome() >= 0)
                .map(rider -> frame.outcomes().get(rider.outcome()))
                .findFirst();
    }

    public double utility(Player player, String outcome) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(player);
        int index = isEmpty() ? -1 : frame.outcomes().indexOf(outcome);
        return voter < 0 || index < 0 ? 0 : utilities()[voter][index];
    }

    public int votesLeft(Player player) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(player);
        return voter < 0 ? 0 : votes[voter];
    }

    public double expectedUtility(Player player) {
        return expected(outcome -> utility(player, outcome)) - unvotedThreatPayoff(player);
    }

    double unvotedThreatPayoff(Player player) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(player);
        if (voter < 0 || !AiExperiments.enabled(frame.viewer(), Experiment.GALACTIC_THREAT_COST)) return 0;
        return riders.stream()
                .filter(rider -> rider.owner() == voter && rider.outcome() >= 0)
                .filter(rider -> GalacticThreatCost.RIDER.equals(rider.title()))
                .mapToDouble(rider -> {
                    String outcome = frame.outcomes().get(rider.outcome());
                    return rider.payoff() * Math.max(0, chance(outcome) - votedChance(outcome));
                })
                .sum();
    }

    public AgendaForecast withRider(Player owner, String title, String outcome) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(owner);
        if (voter < 0) return this;
        List<Rider> more = new ArrayList<>(riders);
        more.add(rider(frame, voter, title, frame.outcomes().indexOf(outcome)));
        int[] remaining = votes.clone();
        if (!frame.keepsVotesWithRider()[voter]) remaining[voter] = 0;
        return new AgendaForecast(frame, remaining, cast, castBy, more);
    }

    public AgendaForecast withVotes(Player seat, String outcome, int count) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(seat);
        if (voter < 0) return this;
        int[] remaining = votes.clone();
        remaining[voter] = 0;
        int[] placed = cast.clone();
        int index = frame.outcomes().indexOf(outcome);
        int[][] by = new int[castBy.length][];
        for (int other = 0; other < by.length; other++) by[other] = castBy[other].clone();
        if (index >= 0) {
            placed[index] += count;
            by[voter][index] += count;
        }
        return new AgendaForecast(frame, remaining, placed, by, riders);
    }

    public AgendaForecast without(Player silenced) {
        int voter = isEmpty() ? -1 : frame.voters().indexOf(silenced);
        if (voter < 0) return this;
        int[] remaining = votes.clone();
        remaining[voter] = 0;
        return new AgendaForecast(frame, remaining, cast, castBy, riders);
    }

    private static boolean applies(Game game) {
        return !game.isFowMode()
                && !game.isHiddenAgendaMode()
                && !game.isOmegaPhaseMode()
                && game.getPhaseOfGame() != null
                && game.getPhaseOfGame().toLowerCase().startsWith(AGENDA_PHASE);
    }

    private static List<Player> votingOrder(Game game) {
        List<Player> order = new ArrayList<>(Helper.getSpeakerOrFullPriorityOrder(game));
        if (order.isEmpty()) order.addAll(game.getRealPlayers());
        order.removeIf(player -> !game.getRealPlayers().contains(player));
        int speaker = order.indexOf(game.getSpeaker());
        if (speaker >= 0) Collections.rotate(order, order.size() - speaker - 1);
        return order;
    }

    private static boolean[] keepsVotesWithRider(Game game, List<Player> voters) {
        boolean[] keeps = new boolean[voters.size()];
        for (int voter = 0; voter < keeps.length; voter++) {
            keeps[voter] = game.playerHasLeaderUnlockedOrAlliance(voters.get(voter), XXCHA_COMMANDER);
        }
        return keeps;
    }

    private static int speakerIndex(Game game, List<Player> voters) {
        int speaker = voters.indexOf(game.getSpeaker());
        return speaker < 0 ? voters.size() - 1 : speaker;
    }

    private static double[][] baseUtilities(
            Game game, Player viewer, List<Player> voters, List<String> outcomes, String agenda) {
        double[][] base = new double[voters.size()][outcomes.size()];
        for (int voter = 0; voter < voters.size(); voter++) {
            for (int outcome = 0; outcome < outcomes.size(); outcome++) {
                base[voter][outcome] = AgendaUtility.of(
                        game, voters.get(voter), voters.get(voter) == viewer, agenda, outcomes.get(outcome));
            }
        }
        return base;
    }

    private static double[][] draws(Game game, Player viewer, int voters) {
        SplittableRandom random =
                new SplittableRandom(Objects.hash(game.getName(), game.getCurrentAgendaInfo(), viewer.getFaction()));
        double[][] draws = new double[SAMPLES][voters];
        for (double[] sample : draws) {
            for (int voter = 0; voter < voters; voter++) sample[voter] = random.nextDouble();
        }
        return draws;
    }

    private static Placed readPlaced(Game game, Player viewer, List<Player> voters, List<String> outcomes) {
        int[] byOutcome = new int[outcomes.size()];
        int[] byVoter = new int[voters.size()];
        int[][] by = new int[voters.size()][outcomes.size()];
        List<Rider> riders = new ArrayList<>();
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            int outcome = outcomes.indexOf(entry.getKey());
            for (String piece : entry.getValue().split(";")) {
                int split = piece.indexOf('_');
                if (split < 0) continue;
                int voter = voters.indexOf(game.getPlayerFromColorOrFaction(
                        piece.substring(0, split).toLowerCase()));
                String what = piece.substring(split + 1);
                if (voter < 0) continue;
                if (NumberUtils.isDigits(what)) {
                    byVoter[voter] += Integer.parseInt(what);
                    if (outcome >= 0) {
                        byOutcome[outcome] += Integer.parseInt(what);
                        by[voter][outcome] += Integer.parseInt(what);
                    }
                } else if (what.contains("Rider") || what.contains("Sanction")) {
                    riders.add(riderOf(game, viewer, voters, voter, what, outcome));
                }
            }
        }
        return new Placed(byOutcome, byVoter, by, riders);
    }

    private static Rider riderOf(Game game, Player viewer, List<Player> voters, int voter, String title, int outcome) {
        Player owner = voters.get(voter);
        return new Rider(
                voter,
                title,
                outcome,
                RiderPayoff.of(game, viewer, owner, title),
                AgendaUtility.riderStake(game, owner));
    }

    private static Rider rider(Frame frame, int voter, String title, int outcome) {
        return riderOf(frame.game(), frame.viewer(), frame.voters(), voter, title, outcome);
    }

    private static int[] remainingVotes(Game game, Frame frame, Placed placed) {
        Map<String, Integer> snapshot = AgendaHelper.getAgendaStartVoteCounts(game);
        int activeVoter = activeVoter(game, frame.voters());
        int[] remaining = new int[frame.voters().size()];
        for (int voter = 0; voter < remaining.length; voter++) {
            Player player = frame.voters().get(voter);
            boolean done = isEnded(game) || voter < activeVoter;
            if (done || blocked(game, player, hasRider(placed, voter), frame.keepsVotesWithRider()[voter])) continue;
            int left = Math.max(0, snapshot.getOrDefault(player.getColor(), 0) - placed.castByVoter()[voter]);
            remaining[voter] = Math.max(0, left - keptBack(game, frame.viewer(), player));
        }
        return remaining;
    }

    private static int keptBack(Game game, Player viewer, Player player) {
        if (!AgendaRound.keepsAVoteBack(game) || !AiExperiments.enabled(viewer, Experiment.AGENDA_VALUE_TABLE)) {
            return 0;
        }
        return BallotPlanner.reserve(AgendaVoting.readiedPlanetVotes(game, player));
    }

    private static boolean hasRider(Placed placed, int voter) {
        return placed.riders().stream().anyMatch(rider -> rider.owner() == voter);
    }

    private static boolean blocked(Game game, Player player, boolean riding, boolean keepsVotes) {
        if (AgendaVoting.cannotVote(game, player)) return true;
        if (keepsVotes) return false;
        return riding || game.getStoredValue(ASSASSINATED).contains(player.getFaction());
    }

    private static boolean isEnded(Game game) {
        return END_PHASE.equalsIgnoreCase(game.getPhaseOfGame());
    }

    private static int activeVoter(Game game, List<Player> voters) {
        if (!VOTING_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) return 0;
        for (int voter = 0; voter < voters.size(); voter++) {
            if (voters.get(voter).getUserID().equals(game.getActivePlayerID())) return voter;
        }
        return 0;
    }

    private double[][] utilities() {
        if (utilities != null) return utilities;
        double[][] merged = new double[frame.base().length][];
        for (int voter = 0; voter < merged.length; voter++) merged[voter] = frame.base()[voter].clone();
        for (Rider rider : riders) {
            if (rider.outcome() < 0) continue;
            for (int voter = 0; voter < merged.length; voter++) {
                if (voter == rider.owner()) {
                    merged[voter][rider.outcome()] += rider.payoff();
                } else if (RiderPayoff.isImperial(rider.title())) {
                    merged[voter][rider.outcome()] -= rider.rivalStake();
                }
                merged[voter][rider.outcome()] -= threatDislike(voter, rider);
            }
        }
        utilities = merged;
        return merged;
    }

    private double threatDislike(int voter, Rider rider) {
        if (!GalacticThreatCost.RIDER.equals(rider.title())) return 0;
        Player player = frame.voters().get(voter);
        if (player == frame.viewer() || !AiExperiments.enabled(frame.viewer(), Experiment.GALACTIC_THREAT_COST)) {
            return 0;
        }
        String outcome = frame.outcomes().get(rider.outcome());
        return GalacticThreatCost.cost(frame.game(), player, frame.voters().get(rider.owner()), outcome, outcome);
    }

    private void simulate() {
        double[][] utility = utilities();
        int voters = utility.length;
        int outcomes = frame.outcomes().size();
        double[][] cumulative = new double[voters][];
        boolean[] indifferent = new boolean[voters];
        for (int voter = 0; voter < voters; voter++) {
            indifferent[voter] = spread(utility[voter]) <= INDIFFERENT_SPREAD;
            cumulative[voter] = cumulative(utility[voter]);
        }
        boolean abstainsWhenIndifferent = AiExperiments.enabled(frame.viewer(), Experiment.AGENDA_VALUE_TABLE);
        int[] wins = new int[outcomes];
        int[] votedWins = new int[outcomes];
        boolean[] predictor = predictors(voters);
        for (double[] draw : frame.draws()) {
            int[] tally = cast.clone();
            int[] predictorTally = predictorCast(predictor, outcomes);
            for (int voter = 0; voter < voters; voter++) {
                if (votes[voter] == 0) continue;
                int pick = indifferent[voter]
                        ? (abstainsWhenIndifferent ? -1 : indifferentPick(draw[voter], outcomes))
                        : softmaxPick(cumulative[voter], draw[voter]);
                if (pick < 0) continue;
                tally[pick] += votes[voter];
                if (predictor[voter]) predictorTally[pick] += votes[voter];
            }
            int winner = winner(tally, utility[frame.speaker()]);
            wins[winner]++;
            if (tally[winner] > predictorTally[winner]) votedWins[winner]++;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        Map<String, Double> voted = new LinkedHashMap<>();
        for (int outcome = 0; outcome < outcomes; outcome++) {
            result.put(frame.outcomes().get(outcome), (double) wins[outcome] / SAMPLES);
            voted.put(frame.outcomes().get(outcome), (double) votedWins[outcome] / SAMPLES);
        }
        chances = Collections.unmodifiableMap(result);
        votedChances = Collections.unmodifiableMap(voted);
    }

    private boolean[] predictors(int voters) {
        boolean[] predictor = new boolean[voters];
        for (Rider rider : riders) {
            if (GalacticThreatCost.RIDER.equals(rider.title())) predictor[rider.owner()] = true;
        }
        return predictor;
    }

    private int[] predictorCast(boolean[] predictor, int outcomes) {
        int[] placed = new int[outcomes];
        for (int voter = 0; voter < predictor.length; voter++) {
            if (!predictor[voter]) continue;
            for (int outcome = 0; outcome < outcomes; outcome++) placed[outcome] += castBy[voter][outcome];
        }
        return placed;
    }

    private static double spread(double[] utility) {
        return Arrays.stream(utility).max().orElse(0)
                - Arrays.stream(utility).min().orElse(0);
    }

    private static double[] cumulative(double[] utility) {
        double best = Arrays.stream(utility).max().orElse(0);
        double[] cumulative = new double[utility.length];
        double sum = 0;
        for (int outcome = 0; outcome < utility.length; outcome++) {
            sum += Math.exp((utility[outcome] - best) / TEMPERATURE);
            cumulative[outcome] = sum;
        }
        for (int outcome = 0; outcome < utility.length; outcome++) cumulative[outcome] /= sum;
        return cumulative;
    }

    private static int indifferentPick(double draw, int outcomes) {
        if (draw < ABSTAINS_WHEN_INDIFFERENT) return -1;
        double rescaled = (draw - ABSTAINS_WHEN_INDIFFERENT) / (1 - ABSTAINS_WHEN_INDIFFERENT);
        return Math.min(outcomes - 1, (int) (rescaled * outcomes));
    }

    private static int softmaxPick(double[] cumulative, double draw) {
        for (int outcome = 0; outcome < cumulative.length; outcome++) {
            if (draw <= cumulative[outcome]) return outcome;
        }
        return cumulative.length - 1;
    }

    private static int winner(int[] tally, double[] speakerUtility) {
        int best = 0;
        for (int outcome = 1; outcome < tally.length; outcome++) {
            boolean more = tally[outcome] > tally[best];
            boolean tiedAndPreferred = tally[outcome] == tally[best] && speakerUtility[outcome] > speakerUtility[best];
            if (more || tiedAndPreferred) best = outcome;
        }
        return best;
    }
}
