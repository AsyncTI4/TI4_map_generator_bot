package ti4.service.statistics.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.helpers.StatusHelper;
import ti4.image.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.StrategyCardSetModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class MatchmakingGameRankEvaluator {

    private static final Set<String> SECRETS_THAT_CANNOT_BE_SCORED_ON_DEMAND = Set.of("ttfd", "bam", "pe");
    private static final String PROVE_ENDURANCE = "pe";
    private static final String ACTION_PHASE_CODE = "AP";
    private static final String STATUS_PHASE_CODE = "SP";
    private static final String ACTION_PHASE = "action";
    private static final String IMPERIAL_AUTOMATION_ID = "pok8imperial";
    private static final String TWILIGHTS_FALL_IMPERIAL_AUTOMATION_ID = "tf8";
    private static final String IMPERIAL_CARD_NAME = "imperial";
    private static final String AETERNA_CARD_NAME = "aeterna";
    private static final int MECATOL_IMPERIAL_POINT = 1;
    private static final int WINNER_RANK = 1;
    private static final int FIRST_RANK_BELOW_WINNER = 2;

    public record SimulatedStanding(int rank, int simulatedScore) {}

    public static Map<String, Integer> evaluate(Game game) {
        Map<String, Integer> ranks = new HashMap<>();
        evaluateStandings(game).forEach((userId, standing) -> ranks.put(userId, standing.rank()));
        return ranks;
    }

    public static Map<String, SimulatedStanding> evaluateStandings(Game game) {
        if (!game.isHasEnded()) {
            return Map.of();
        }
        List<Player> winners = game.getWinners();
        if (winners.size() != 1) {
            return Map.of();
        }

        Player winner = winners.getFirst();
        List<Player> contenders = game.getRealAndEliminatedPlayers().stream()
                .filter(player -> !isSamePlayer(player, winner))
                .toList();

        Simulation simulation = new Simulation(game.getVp());
        contenders.forEach(simulation::seed);

        String phaseCode = EndingRoundPhaseStatisticsService.normalizePhaseCode(game.getPhaseOfGame());
        if (ACTION_PHASE_CODE.equals(phaseCode)) {
            simulateActionPhase(game, simulation, contenders);
        } else if (STATUS_PHASE_CODE.equals(phaseCode)) {
            simulateStatusPhase(game, simulation, contenders, winner);
        }

        return buildStandings(winner, contenders, simulation);
    }

    public static boolean isExcludedForWinnerCount(Game game) {
        return someoneReachedTheGoal(game) && game.getWinners().size() != 1;
    }

    private static boolean someoneReachedTheGoal(Game game) {
        return game.isHasEnded() && game.getHighestScore() >= game.getVp();
    }

    private static void simulateActionPhase(Game game, Simulation simulation, List<Player> contenders) {
        List<Player> initiativeOrder = game.getActionPhaseTurnOrder().stream()
                .filter(player -> containsPlayer(contenders, player))
                .toList();
        Player imperialHolder = findReadiedImperialHolder(game, initiativeOrder);

        boolean imperialBonusPending = imperialHolder != null;
        boolean firstPass = true;
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Player player : initiativeOrder) {
                if (simulation.isFinished(player)) {
                    continue;
                }
                if (firstPass && imperialBonusPending && isSamePlayer(player, imperialHolder)) {
                    imperialBonusPending = false;
                    simulation.award(player, imperialPrimaryPoints(game, player));
                    progressed = true;
                    continue;
                }
                String secretId = nextOnDemandActionSecret(player, simulation);
                if (secretId == null) {
                    continue;
                }
                simulation.consume(player, secretId);
                simulation.award(player, secretPoints(secretId));
                progressed = true;
            }
            firstPass = false;
        }

        for (Player player : initiativeOrder) {
            if (!simulation.isFinished(player) && player.getSecretsUnscored().containsKey(PROVE_ENDURANCE)) {
                simulation.award(player, secretPoints(PROVE_ENDURANCE));
            }
        }
    }

    private static void simulateStatusPhase(Game game, Simulation simulation, List<Player> contenders, Player winner) {
        List<Player> scoringOrder = StatusHelper.getPlayersInScoringOrder(game);
        int winnerIndex = indexOfPlayer(scoringOrder, winner);
        if (winnerIndex < 0) {
            return;
        }
        for (Player player : scoringOrder.subList(winnerIndex + 1, scoringOrder.size())) {
            if (!containsPlayer(contenders, player)) {
                continue;
            }
            simulation.award(
                    player,
                    bestScoreableStatusSecretPoints(game, player) + bestQualifyingPublicObjectivePoints(game, player));
        }
    }

    private static Map<String, SimulatedStanding> buildStandings(
            Player winner, List<Player> contenders, Simulation simulation) {
        Map<String, SimulatedStanding> standings = new HashMap<>();
        standings.put(winner.getUserID(), new SimulatedStanding(WINNER_RANK, winner.getTotalVictoryPoints()));

        for (int i = 0; i < simulation.crossedGoalInOrder.size(); i++) {
            String userId = simulation.crossedGoalInOrder.get(i);
            standings.put(userId, new SimulatedStanding(FIRST_RANK_BELOW_WINNER + i, simulation.scoreOf(userId)));
        }

        List<Player> remaining = contenders.stream()
                .filter(player -> !simulation.isFinished(player))
                .sorted(Comparator.comparingInt(simulation::score).reversed().thenComparing(Player::getUserID))
                .toList();

        int firstRemainingRank = FIRST_RANK_BELOW_WINNER + simulation.crossedGoalInOrder.size();
        int rankOfCurrentScore = firstRemainingRank;
        Integer currentScore = null;
        for (int i = 0; i < remaining.size(); i++) {
            Player player = remaining.get(i);
            int score = simulation.score(player);
            if (currentScore == null || score != currentScore) {
                currentScore = score;
                rankOfCurrentScore = firstRemainingRank + i;
            }
            standings.put(player.getUserID(), new SimulatedStanding(rankOfCurrentScore, score));
        }
        return standings;
    }

    private static String nextOnDemandActionSecret(Player player, Simulation simulation) {
        return player.getSecretsUnscored().keySet().stream()
                .filter(secretId -> !SECRETS_THAT_CANNOT_BE_SCORED_ON_DEMAND.contains(secretId))
                .filter(secretId -> !simulation.hasConsumed(player, secretId))
                .filter(MatchmakingGameRankEvaluator::isActionPhaseSecret)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    private static boolean isActionPhaseSecret(String secretId) {
        SecretObjectiveModel secretObjective = Mapper.getSecretObjective(secretId);
        return secretObjective != null && ACTION_PHASE.equalsIgnoreCase(secretObjective.getPhase());
    }

    private static int secretPoints(String secretId) {
        SecretObjectiveModel secretObjective = Mapper.getSecretObjective(secretId);
        return secretObjective == null ? 0 : secretObjective.getPoints();
    }

    private static int bestScoreableStatusSecretPoints(Game game, Player player) {
        return ListPlayerInfoService.getScoreableStatusPhaseSecrets(game, player).stream()
                .mapToInt(MatchmakingGameRankEvaluator::secretPoints)
                .max()
                .orElse(0);
    }

    private static int bestQualifyingPublicObjectivePoints(Game game, Player player) {
        if (!Helper.canPlayerScorePOs(game, player)) {
            return 0;
        }
        List<String> qualifying = ListPlayerInfoService.getQualifyingPublicObjectiveIds(game, player);
        if (qualifying.isEmpty()) {
            return 0;
        }
        Integer points = ListPlayerInfoService.getPublicObjectivePoints(qualifying.getFirst());
        return points == null ? 0 : points;
    }

    private static int imperialPrimaryPoints(Game game, Player player) {
        int points = bestQualifyingPublicObjectivePoints(game, player);
        if (player.controlsMecatol(true)) {
            points += MECATOL_IMPERIAL_POINT;
        }
        return points;
    }

    private static Player findReadiedImperialHolder(Game game, List<Player> candidates) {
        Integer imperialInitiative = findImperialInitiative(game);
        if (imperialInitiative == null) {
            return null;
        }
        return candidates.stream()
                .filter(player -> player.getSCs().contains(imperialInitiative))
                .filter(player -> !player.getExhaustedSCs().contains(imperialInitiative))
                .findFirst()
                .orElse(null);
    }

    private static Integer findImperialInitiative(Game game) {
        StrategyCardSetModel strategyCardSet = game.getStrategyCardSet();
        if (strategyCardSet == null) {
            return null;
        }
        return strategyCardSet.getStrategyCardModels().stream()
                .filter(Objects::nonNull)
                .filter(card -> card.usesAutomationForSCID(IMPERIAL_AUTOMATION_ID)
                        || card.usesAutomationForSCID(TWILIGHTS_FALL_IMPERIAL_AUTOMATION_ID))
                .findFirst()
                .or(() -> findCardByName(strategyCardSet, IMPERIAL_CARD_NAME))
                .or(() -> findCardByName(strategyCardSet, AETERNA_CARD_NAME))
                .map(StrategyCardModel::getInitiative)
                .orElse(null);
    }

    private static Optional<StrategyCardModel> findCardByName(StrategyCardSetModel strategyCardSet, String name) {
        return strategyCardSet.getStrategyCardModels().stream()
                .filter(Objects::nonNull)
                .filter(card -> name.equalsIgnoreCase(card.getName()))
                .findFirst();
    }

    private static boolean isSamePlayer(Player left, Player right) {
        return left != null && right != null && Objects.equals(left.getUserID(), right.getUserID());
    }

    private static boolean containsPlayer(List<Player> players, Player target) {
        return players.stream().anyMatch(player -> isSamePlayer(player, target));
    }

    private static int indexOfPlayer(List<Player> players, Player target) {
        for (int i = 0; i < players.size(); i++) {
            if (isSamePlayer(players.get(i), target)) {
                return i;
            }
        }
        return -1;
    }

    private static final class Simulation {

        private final int goal;
        private final Map<String, Integer> scoreByUserId = new HashMap<>();
        private final Map<String, Set<String>> consumedSecretsByUserId = new HashMap<>();
        private final List<String> crossedGoalInOrder = new ArrayList<>();
        private final Set<String> finished = new HashSet<>();

        private Simulation(int goal) {
            this.goal = goal;
        }

        private void seed(Player player) {
            scoreByUserId.put(player.getUserID(), player.getTotalVictoryPoints());
        }

        private boolean isFinished(Player player) {
            return finished.contains(player.getUserID());
        }

        private int score(Player player) {
            return scoreOf(player.getUserID());
        }

        private int scoreOf(String userId) {
            return scoreByUserId.getOrDefault(userId, 0);
        }

        private void award(Player player, int points) {
            if (points <= 0 || isFinished(player)) {
                return;
            }
            int updated = scoreByUserId.merge(player.getUserID(), points, Integer::sum);
            if (updated >= goal) {
                finished.add(player.getUserID());
                crossedGoalInOrder.add(player.getUserID());
            }
        }

        private boolean hasConsumed(Player player, String secretId) {
            return consumedSecretsByUserId
                    .getOrDefault(player.getUserID(), Set.of())
                    .contains(secretId);
        }

        private void consume(Player player, String secretId) {
            consumedSecretsByUserId
                    .computeIfAbsent(player.getUserID(), userId -> new HashSet<>())
                    .add(secretId);
        }
    }
}
