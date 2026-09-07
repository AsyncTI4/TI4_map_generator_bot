package ti4.service.statistics.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import lombok.Getter;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.statistics.game.MatchmakingGameRankEvaluator.SimulatedStanding;

public class MatchmakingRankTieAnalysis {

    private static final String INITIATIVE = "initiative";
    private static final int MINIMUM_RATED_PLAYERS = 5;
    private static final int MAXIMUM_RATED_PLAYERS = 8;

    private static final Map<String, ToIntFunction<Player>> TIE_BREAKER_CANDIDATES = buildTieBreakerCandidates();

    private final int sampleSize;

    @Getter
    private final List<String> samples = new ArrayList<>();

    private int rankedGames;
    private int gamesWithTies;
    private long tiedPairs;
    private long totalAdjacentPairs;
    private long tiedAdjacentPairs;
    private long pairsSeparatedByAnyBoardMetric;
    private int gamesOutsideTheRatedCorpus;
    private String lastSampledGame;
    private final Map<Integer, Integer> tieGroupSizes = new TreeMap<>();
    private final Map<String, Integer> tiesByPhase = new TreeMap<>();
    private final Map<String, Long> separatedPairsByCandidate = new LinkedHashMap<>();

    public MatchmakingRankTieAnalysis(int sampleSize) {
        this.sampleSize = sampleSize;
        TIE_BREAKER_CANDIDATES.keySet().forEach(name -> separatedPairsByCandidate.put(name, 0L));
    }

    private static Map<String, ToIntFunction<Player>> buildTieBreakerCandidates() {
        Map<String, ToIntFunction<Player>> candidates = new LinkedHashMap<>();
        candidates.put("victoryPoints", Player::getTotalVictoryPoints);
        candidates.put("planets", player -> player.getPlanets().size());
        candidates.put("secretsInHand", player -> player.getSecretsUnscored().size());
        candidates.put("techs", player -> player.getTechs().size());
        candidates.put("tradeGoods", Player::getTg);
        candidates.put("turnsTaken", Player::getNumberOfTurns);
        candidates.put(INITIATIVE, Player::getInitiative);
        return candidates;
    }

    public void consume(Game game) {
        if (!isRatedByMatchmaking(game)) {
            if (game.isHasEnded()) {
                gamesOutsideTheRatedCorpus++;
            }
            return;
        }
        Map<String, SimulatedStanding> standings;
        try {
            standings = MatchmakingGameRankEvaluator.evaluateStandings(game);
        } catch (Exception e) {
            return;
        }
        if (standings.isEmpty()) {
            return;
        }
        rankedGames++;
        countAdjacentPairs(standings);

        Map<Integer, List<Player>> playersByRank = new TreeMap<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            SimulatedStanding standing = standings.get(player.getUserID());
            if (standing == null) continue;
            playersByRank
                    .computeIfAbsent(standing.rank(), rank -> new ArrayList<>())
                    .add(player);
        }

        boolean gameHadTie = false;
        for (Map.Entry<Integer, List<Player>> entry : playersByRank.entrySet()) {
            List<Player> tied = entry.getValue();
            if (tied.size() < 2) continue;
            gameHadTie = true;
            tieGroupSizes.merge(tied.size(), 1, Integer::sum);
            tiesByPhase.merge(phaseOf(game), 1, Integer::sum);
            scoreCandidates(tied);
            recordSample(game, standings, entry.getKey(), tied);
        }
        if (gameHadTie) {
            gamesWithTies++;
        }
    }

    private static boolean isRatedByMatchmaking(Game game) {
        int playerCount = game.getRealAndEliminatedPlayers().size();
        return !game.isAllianceMode() && playerCount >= MINIMUM_RATED_PLAYERS && playerCount <= MAXIMUM_RATED_PLAYERS;
    }

    private void countAdjacentPairs(Map<String, SimulatedStanding> standings) {
        List<Integer> ranks = standings.values().stream()
                .map(SimulatedStanding::rank)
                .sorted()
                .toList();
        for (int i = 0; i + 1 < ranks.size(); i++) {
            totalAdjacentPairs++;
            if (ranks.get(i).equals(ranks.get(i + 1))) {
                tiedAdjacentPairs++;
            }
        }
    }

    private void scoreCandidates(List<Player> tied) {
        for (int i = 0; i < tied.size(); i++) {
            for (int j = i + 1; j < tied.size(); j++) {
                tiedPairs++;
                boolean separatedByBoardMetric = false;
                for (Map.Entry<String, ToIntFunction<Player>> candidate : TIE_BREAKER_CANDIDATES.entrySet()) {
                    int left = candidate.getValue().applyAsInt(tied.get(i));
                    int right = candidate.getValue().applyAsInt(tied.get(j));
                    if (left == right) continue;
                    separatedPairsByCandidate.merge(candidate.getKey(), 1L, Long::sum);
                    if (!INITIATIVE.equals(candidate.getKey())) {
                        separatedByBoardMetric = true;
                    }
                }
                if (separatedByBoardMetric) {
                    pairsSeparatedByAnyBoardMetric++;
                }
            }
        }
    }

    private void recordSample(Game game, Map<String, SimulatedStanding> standings, int rank, List<Player> tied) {
        if (samples.size() >= sampleSize || game.getName().equals(lastSampledGame)) {
            return;
        }
        lastSampledGame = game.getName();
        StringBuilder sample = new StringBuilder();
        sample.append('`')
                .append(game.getName())
                .append("` phase=")
                .append(phaseOf(game))
                .append(" goal=")
                .append(game.getVp())
                .append(" rank=")
                .append(rank)
                .append(" simulatedTotal=")
                .append(standings.get(tied.getFirst().getUserID()).simulatedScore())
                .append('\n');
        for (Player player : tied) {
            sample.append("  ").append(player.getFaction());
            for (Map.Entry<String, ToIntFunction<Player>> candidate : TIE_BREAKER_CANDIDATES.entrySet()) {
                sample.append(' ')
                        .append(candidate.getKey())
                        .append('=')
                        .append(candidate.getValue().applyAsInt(player));
            }
            sample.append('\n');
        }
        samples.add(sample.toString());
    }

    private static String phaseOf(Game game) {
        String phase = game.getPhaseOfGame();
        return phase == null || phase.isBlank() ? "(none)" : phase;
    }

    public String summary() {
        StringBuilder summary = new StringBuilder();
        summary.append("## Simulated matchmaking rank ties\n");
        summary.append("- ranked games: ").append(rankedGames).append('\n');
        summary.append("- ended games skipped as outside the rated 5-8 player corpus: ")
                .append(gamesOutsideTheRatedCorpus)
                .append('\n');
        summary.append("- games with at least one tie: ")
                .append(gamesWithTies)
                .append(percentOf(gamesWithTies, rankedGames))
                .append('\n');
        summary.append("- adjacent pair tie rate: ")
                .append(String.format(
                        "%.4f (%d/%d)",
                        totalAdjacentPairs == 0 ? 0.0 : tiedAdjacentPairs / (double) totalAdjacentPairs,
                        tiedAdjacentPairs,
                        totalAdjacentPairs))
                .append('\n');
        summary.append("- tied pairs: ").append(tiedPairs).append('\n');
        summary.append("- tie group sizes: ").append(tieGroupSizes).append('\n');
        summary.append("- ties by ending phase: ")
                .append(sortedByValue(tiesByPhase))
                .append('\n');
        summary.append("\n### Tied pairs a candidate tie-breaker would separate\n");
        for (Map.Entry<String, Long> entry : separatedPairsByCandidate.entrySet()) {
            summary.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append(percentOf(entry.getValue(), tiedPairs))
                    .append('\n');
        }
        summary.append("- any board metric except ")
                .append(INITIATIVE)
                .append(": ")
                .append(pairsSeparatedByAnyBoardMetric)
                .append(percentOf(pairsSeparatedByAnyBoardMetric, tiedPairs))
                .append('\n');
        return summary.toString();
    }

    private static String percentOf(long value, long total) {
        if (total == 0) {
            return " (0.0%)";
        }
        return String.format(" (%.1f%%)", value * 100.0 / total);
    }

    private static Map<String, Integer> sortedByValue(Map<String, Integer> counts) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
