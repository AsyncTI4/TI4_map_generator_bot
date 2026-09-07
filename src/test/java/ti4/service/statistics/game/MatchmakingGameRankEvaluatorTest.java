package ti4.service.statistics.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

class MatchmakingGameRankEvaluatorTest extends BaseTi4Test {

    private static final int VICTORY_POINT_GOAL = 10;
    private static final int IMPERIAL_STRATEGY_CARD = 8;
    private static final String MECATOL_REX = "mr";

    // Status-phase secrets, only ever used to hand out victory points. Never placed in a hand,
    // so they cannot interfere with the action-phase simulation.
    private static final List<String> SCORING_SECRETS =
            List.of("pem", "otf", "mtm", "hrm", "eh", "dhw", "dfat", "te", "ose", "mrm", "mlp", "mp");

    // Action-phase secrets that the simulation is allowed to score on demand.
    private static final String ONE_POINT_ACTION_SECRET = "dtgs";
    private static final String ANOTHER_ONE_POINT_ACTION_SECRET = "uf";
    private static final String TWO_POINT_ACTION_SECRET = "savior";

    // Action-phase secrets the spec excludes from the loop.
    private static final String TURN_THEIR_FLEETS_TO_DUST = "ttfd";
    private static final String BECOME_A_MARTYR = "bam";
    private static final String PROVE_ENDURANCE = "pe";

    // Adapt New Strategies: a status-phase secret that qualifies at 2 faction techs.
    private static final String ADAPT_NEW_STRATEGIES = "ans";
    private static final List<String> TWO_FACTION_TECHS = List.of("lw2", "l4");

    private static final List<String> FACTIONS = List.of("sol", "hacan", "letnev", "xxcha", "arborec", "saar");
    private static final List<String> COLORS = List.of("blue", "red", "green", "yellow", "black", "purple");

    @Test
    void returnsNoRanksWhenTheGameHasNotEnded() {
        Game game = endedGame("action");
        game.setHasEnded(false);
        winnerWith(game, "winner", 1);
        playerWith(game, "loser", 2, 5);

        assertThat(MatchmakingGameRankEvaluator.evaluate(game)).isEmpty();
    }

    @Test
    void returnsNoRanksWhenNobodyReachedTheGoal() {
        Game game = endedGame("action");
        playerWith(game, "first", 1, 9);
        playerWith(game, "second", 2, 5);

        assertThat(MatchmakingGameRankEvaluator.evaluate(game)).isEmpty();
    }

    @Test
    void ranksTheWinnerFirst() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        playerWith(game, "second", 2, 8);
        playerWith(game, "third", 3, 4);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("winner", 1);
        assertThat(ranks).hasSize(3);
    }

    @Test
    void actionPhaseSecretsAreScoredUntilTheHandIsDrained() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player climber = playerWith(game, "climber", 2, 8);
        climber.setSecret(ONE_POINT_ACTION_SECRET);
        climber.setSecret(ANOTHER_ONE_POINT_ACTION_SECRET);
        playerWith(game, "static", 3, 8);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("winner", 1).containsEntry("climber", 2).containsEntry("static", 3);
    }

    @Test
    void twoPointActionSecretsAreWorthTwoSimulatedPoints() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player doubleScorer = playerWith(game, "double", 2, 8);
        doubleScorer.setSecret(TWO_POINT_ACTION_SECRET);
        Player singleScorer = playerWith(game, "single", 3, 8);
        singleScorer.setSecret(ONE_POINT_ACTION_SECRET);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("double", 2).containsEntry("single", 3);
    }

    @Test
    void secretsThatCannotBeScoredOnDemandAreSkipped() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player blocked = playerWith(game, "blocked", 2, 8);
        blocked.setSecret(TURN_THEIR_FLEETS_TO_DUST);
        blocked.setSecret(BECOME_A_MARTYR);
        Player scoring = playerWith(game, "scoring", 3, 8);
        scoring.setSecret(ONE_POINT_ACTION_SECRET);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("scoring", 2).containsEntry("blocked", 3);
    }

    @Test
    void proveEnduranceIsAwardedAfterTheLoop() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player enduring = playerWith(game, "enduring", 2, 9);
        enduring.setSecret(PROVE_ENDURANCE);
        playerWith(game, "other", 3, 9);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("enduring", 2).containsEntry("other", 3);
    }

    @Test
    void theOrderOfCrossingTheGoalDeterminesRank() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player earlyInitiative = playerWith(game, "early", 2, 9);
        earlyInitiative.setSecret(ONE_POINT_ACTION_SECRET);
        Player lateInitiative = playerWith(game, "late", 3, 9);
        lateInitiative.setSecret(ONE_POINT_ACTION_SECRET);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("early", 2).containsEntry("late", 3);
    }

    @Test
    void aReadiedImperialHolderGainsTheMecatolPoint() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player imperial = playerWith(game, "imperial", IMPERIAL_STRATEGY_CARD, 9);
        imperial.addPlanet(MECATOL_REX);
        playerWith(game, "other", 2, 9);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("imperial", 2).containsEntry("other", 3);
    }

    @Test
    void anExhaustedImperialHolderGainsNothing() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player imperial = playerWith(game, "imperial", IMPERIAL_STRATEGY_CARD, 9);
        imperial.addPlanet(MECATOL_REX);
        game.setSCPlayed(IMPERIAL_STRATEGY_CARD, true);
        Player other = playerWith(game, "other", 2, 9);
        other.setSecret(ONE_POINT_ACTION_SECRET);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("other", 2).containsEntry("imperial", 3);
    }

    @Test
    void theImperialHolderStillScoresSecretsOnTheSecondPass() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player imperial = playerWith(game, "imperial", IMPERIAL_STRATEGY_CARD, 8);
        imperial.addPlanet(MECATOL_REX);
        imperial.setSecret(ONE_POINT_ACTION_SECRET);
        playerWith(game, "other", 2, 9);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("imperial", 2).containsEntry("other", 3);
    }

    @Test
    void onlyPlayersAfterTheWinnerScoreInTheStatusPhase() {
        Game game = endedGame("statusScoring");
        Player before = playerWith(game, "before", 1, 8);
        qualifyForAdaptNewStrategies(before);
        winnerWith(game, "winner", 3);
        Player after = playerWith(game, "after", 5, 8);
        qualifyForAdaptNewStrategies(after);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("winner", 1).containsEntry("after", 2).containsEntry("before", 3);
    }

    @Test
    void agendaPhaseGamesAreRankedByPointTotalAlone() {
        Game game = endedGame("agendaEnd");
        winnerWith(game, "winner", 1);
        Player high = playerWith(game, "high", 2, 8);
        high.setSecret(ONE_POINT_ACTION_SECRET);
        high.setSecret(ANOTHER_ONE_POINT_ACTION_SECRET);
        playerWith(game, "tied", 3, 8);
        playerWith(game, "low", 4, 5);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks).containsEntry("winner", 1);
        assertThat(ranks).containsEntry("high", 2).containsEntry("tied", 2);
        assertThat(ranks).containsEntry("low", 4);
    }

    @Test
    void everyRealPlayerReceivesARank() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        playerWith(game, "second", 2, 8);
        playerWith(game, "third", 3, 6);
        playerWith(game, "fourth", 4, 2);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(ranks.keySet()).containsExactlyInAnyOrder("winner", "second", "third", "fourth");
        assertThat(ranks.values()).allMatch(rank -> rank >= 1);
    }

    @Test
    void repeatedEvaluationOfTheSameGameIsStable() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player first = playerWith(game, "first", 2, 7);
        first.setSecret(ONE_POINT_ACTION_SECRET);
        first.setSecret(ANOTHER_ONE_POINT_ACTION_SECRET);
        Player second = playerWith(game, "second", 3, 7);
        second.setSecret(TWO_POINT_ACTION_SECRET);

        assertThat(MatchmakingGameRankEvaluator.evaluate(game)).isEqualTo(MatchmakingGameRankEvaluator.evaluate(game));
    }

    @Test
    void evaluationDoesNotMutateTheGame() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        Player imperial = playerWith(game, "imperial", IMPERIAL_STRATEGY_CARD, 8);
        imperial.addPlanet(MECATOL_REX);
        imperial.setSecret(ONE_POINT_ACTION_SECRET);
        Player other = playerWith(game, "other", 2, 8);
        other.setSecret(TWO_POINT_ACTION_SECRET);
        qualifyForAdaptNewStrategies(other);

        String before = gameSnapshot(game);
        MatchmakingGameRankEvaluator.evaluate(game);

        assertThat(gameSnapshot(game)).isEqualTo(before);
    }

    @Test
    void normalGamesAreNotFlaggedForWinnerCount() {
        Game game = endedGame("action");
        winnerWith(game, "winner", 1);
        playerWith(game, "second", 2, 8);

        assertThat(MatchmakingGameRankEvaluator.isExcludedForWinnerCount(game)).isFalse();
    }

    @Test
    void handlesARealSaveWithoutThrowingOrMutating() {
        try (TestGameHarness harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            String before = gameSnapshot(game);

            Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

            assertThat(gameSnapshot(game)).isEqualTo(before);
            assertThat(ranks.values()).allMatch(rank -> rank >= 1);
        }
    }

    private static void qualifyForAdaptNewStrategies(Player player) {
        player.setSecret(ADAPT_NEW_STRATEGIES);
        TWO_FACTION_TECHS.forEach(player::addTech);
    }

    private static String gameSnapshot(Game game) {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append(game.getScoredPublicObjectives())
                .append(game.getRevealedPublicObjectives())
                .append(game.getCustomPublicVP())
                .append(game.getScPlayed())
                .append(game.getPhaseOfGame());
        for (Player player : game.getRealAndEliminatedPlayers()) {
            snapshot.append(player.getUserID())
                    .append(player.getSecrets())
                    .append(player.getSecretsScored())
                    .append(player.getSCs())
                    .append(player.getPlanets())
                    .append(player.getTechs());
        }
        return snapshot.toString();
    }

    private static Game endedGame(String phase) {
        Game game = new Game();
        game.setName("rank-evaluator-" + UUID.randomUUID());
        game.newGameSetup();
        game.setVp(VICTORY_POINT_GOAL);
        game.setHasEnded(true);
        game.setPhaseOfGame(phase);
        return game;
    }

    private static Player winnerWith(Game game, String userId, int strategyCard) {
        return playerWith(game, userId, strategyCard, VICTORY_POINT_GOAL);
    }

    private static Player playerWith(Game game, String userId, int strategyCard, int victoryPoints) {
        int seat = game.getPlayers().size();
        Player player = game.addPlayer(userId, userId);
        player.setFaction(FACTIONS.get(seat % FACTIONS.size()));
        player.setColor(COLORS.get(seat % COLORS.size()));
        player.setSCs(Set.of(strategyCard));
        AtomicInteger scored = new AtomicInteger();
        while (scored.get() < victoryPoints) {
            player.setSecretScored(SCORING_SECRETS.get(scored.getAndIncrement()));
        }
        return player;
    }
}
