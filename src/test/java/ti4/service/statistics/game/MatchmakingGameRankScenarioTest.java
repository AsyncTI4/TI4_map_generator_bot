package ti4.service.statistics.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Walks the worked example: a Trade (initiative 5) win during the action phase, the action loop
 * resuming at Warfare and wrapping, an Imperial primary consuming a public objective, a status
 * phase for everyone still unranked, and a final tie broken by matchmaking rating.
 */
class MatchmakingGameRankScenarioTest extends BaseTi4Test {

    private static final int VICTORY_POINT_GOAL = 10;

    private static final int LEADERSHIP = 1;
    private static final int DIPLOMACY = 2;
    private static final int POLITICS = 3;
    private static final int TRADE = 5;
    private static final int WARFARE = 6;
    private static final int TECHNOLOGY = 7;
    private static final int IMPERIAL = 8;

    private static final String ONE_POINT_ACTION_SECRET = "dtgs";
    private static final String ANOTHER_ONE_POINT_ACTION_SECRET = "uf";
    private static final String A_THIRD_ONE_POINT_ACTION_SECRET = "mew";
    private static final String STATUS_SECRET_NEEDING_TWO_FACTION_TECHS = "ans";
    private static final List<String> TWO_FACTION_TECHS = List.of("lw2", "l4");

    // Master the Sciences: 2 points, scoreable with two techs in each of the four main types.
    private static final String TWO_POINT_PUBLIC_OBJECTIVE = "master_science";
    private static final List<String> TWO_TECHS_IN_EACH_MAIN_TYPE =
            List.of("amd", "gd", "nm", "dxa", "st", "gls", "ps", "md_base");

    private static final List<String> SCORING_SECRETS =
            List.of("pem", "otf", "mtm", "hrm", "eh", "dhw", "dfat", "te", "ose", "mrm");
    private static final List<String> FACTIONS =
            List.of("sol", "hacan", "letnev", "xxcha", "arborec", "saar", "yin", "naalu");
    private static final List<String> COLORS =
            List.of("blue", "red", "green", "yellow", "black", "purple", "orange", "pink");

    @Test
    void walksTheActionPhaseScenarioThroughToTheRatingTieBreak() {
        Game game = endedGame("action");

        // Won on the Trade card during the action phase.
        addPlayer(game, "trade", TRADE, VICTORY_POINT_GOAL);

        // Next in initiative after the winner: one action secret takes them to the goal.
        Player warfare = addPlayer(game, "warfare", WARFARE, 9);
        warfare.setSecret(ONE_POINT_ACTION_SECRET);

        // Two action secrets: 8 to 9 on the first pass, 9 to 10 on the second.
        Player technology = addPlayer(game, "technology", TECHNOLOGY, 8);
        technology.setSecret(ANOTHER_ONE_POINT_ACTION_SECRET);
        technology.setSecret(A_THIRD_ONE_POINT_ACTION_SECRET);

        // Readied Imperial with a qualifying 2 point objective: 6 to 8, short of the goal.
        Player imperial = addPlayer(game, "imperial", IMPERIAL, 6);
        allowPublicObjectiveScoring(imperial);
        TWO_TECHS_IN_EACH_MAIN_TYPE.forEach(imperial::addTech);
        game.getRevealedPublicObjectives().put(TWO_POINT_PUBLIC_OBJECTIVE, 1);

        // Wraps around to Leadership, who has nothing to score at any point.
        addPlayer(game, "leadership", LEADERSHIP, 3);

        // Scores a status secret once the status phase is reached.
        Player diplomacy = addPlayer(game, "diplomacy", DIPLOMACY, 8);
        diplomacy.setSecret(STATUS_SECRET_NEEDING_TWO_FACTION_TECHS);
        TWO_FACTION_TECHS.forEach(diplomacy::addTech);

        // Nothing to score, so ties with Imperial on 8 and loses the rating tie-break.
        addPlayer(game, "politics", POLITICS, 8);

        ToDoubleFunction<String> ratings = userId -> "imperial".equals(userId) ? 30.0 : 20.0;

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game, ratings);

        assertThat(ranks)
                .containsEntry("trade", 1)
                .containsEntry("warfare", 2)
                .containsEntry("technology", 3)
                .containsEntry("diplomacy", 4)
                .containsEntry("imperial", 5)
                .containsEntry("politics", 6)
                .containsEntry("leadership", 7);
    }

    @Test
    void theImperialObjectiveIsNotAwardedAgainInTheStatusPhase() {
        Game game = endedGame("action");
        addPlayer(game, "trade", TRADE, VICTORY_POINT_GOAL);
        Player imperial = addPlayer(game, "imperial", IMPERIAL, 6);
        allowPublicObjectiveScoring(imperial);
        TWO_TECHS_IN_EACH_MAIN_TYPE.forEach(imperial::addTech);
        game.getRevealedPublicObjectives().put(TWO_POINT_PUBLIC_OBJECTIVE, 1);
        addPlayer(game, "eight", LEADERSHIP, 8);
        addPlayer(game, "seven", DIPLOMACY, 7);
        addPlayer(game, "six", POLITICS, 6);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game);

        // Imperial finishes on 8, not 10: the objective is consumed by the Imperial primary and
        // cannot be scored a second time when the status phase runs.
        assertThat(ranks).containsEntry("trade", 1);
        assertThat(ranks.get("imperial")).isEqualTo(ranks.get("eight"));
        assertThat(ranks).containsEntry("seven", 4).containsEntry("six", 5);
    }

    @Test
    void agendaPhaseEndingsRankByPointsAndBreakTiesByRating() {
        Game game = endedGame("agendaEnd");
        addPlayer(game, "winner", TRADE, VICTORY_POINT_GOAL);
        Player loaded = addPlayer(game, "highRated", WARFARE, 7);
        loaded.setSecret(ONE_POINT_ACTION_SECRET);
        addPlayer(game, "lowRated", TECHNOLOGY, 7);
        addPlayer(game, "middle", DIPLOMACY, 8);
        addPlayer(game, "bottom", POLITICS, 2);

        ToDoubleFunction<String> ratings = userId -> "highRated".equals(userId) ? 30.0 : 20.0;

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game, ratings);

        // No simulation runs, so the held action secret is worth nothing here.
        assertThat(ranks)
                .containsEntry("winner", 1)
                .containsEntry("middle", 2)
                .containsEntry("highRated", 3)
                .containsEntry("lowRated", 4)
                .containsEntry("bottom", 5);
    }

    @Test
    void strategyPhaseEndingsAreTreatedLikeAgendaPhaseEndings() {
        Game game = endedGame("strategy");
        addPlayer(game, "winner", TRADE, VICTORY_POINT_GOAL);
        Player loaded = addPlayer(game, "highRated", WARFARE, 7);
        loaded.setSecret(ONE_POINT_ACTION_SECRET);
        addPlayer(game, "lowRated", TECHNOLOGY, 7);
        addPlayer(game, "middle", DIPLOMACY, 8);
        addPlayer(game, "bottom", POLITICS, 2);

        ToDoubleFunction<String> ratings = userId -> "highRated".equals(userId) ? 30.0 : 20.0;

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game, ratings);

        assertThat(ranks)
                .containsEntry("winner", 1)
                .containsEntry("middle", 2)
                .containsEntry("highRated", 3)
                .containsEntry("lowRated", 4)
                .containsEntry("bottom", 5);
    }

    @Test
    void equalRatingsLeaveThePlayersTied() {
        Game game = endedGame("agendaEnd");
        addPlayer(game, "winner", TRADE, VICTORY_POINT_GOAL);
        addPlayer(game, "tiedA", WARFARE, 7);
        addPlayer(game, "tiedB", TECHNOLOGY, 7);
        addPlayer(game, "third", DIPLOMACY, 5);
        addPlayer(game, "fourth", POLITICS, 2);

        Map<String, Integer> ranks = MatchmakingGameRankEvaluator.evaluate(game, userId -> 25.0);

        assertThat(ranks).containsEntry("tiedA", 2).containsEntry("tiedB", 2);
        assertThat(ranks).containsEntry("third", 4).containsEntry("fourth", 5);
    }

    private static void allowPublicObjectiveScoring(Player player) {
        player.addAbility("nomadic");
    }

    private static Game endedGame(String phase) {
        Game game = new Game();
        game.setName("rank-scenario-" + UUID.randomUUID());
        game.newGameSetup();
        game.setVp(VICTORY_POINT_GOAL);
        game.setHasEnded(true);
        game.setPhaseOfGame(phase);
        return game;
    }

    private static Player addPlayer(Game game, String userId, int strategyCard, int victoryPoints) {
        int seat = game.getPlayers().size();
        Player player = game.addPlayer(userId, userId);
        player.setFaction(FACTIONS.get(seat % FACTIONS.size()));
        player.setColor(COLORS.get(seat % COLORS.size()));
        player.setSCs(Set.of(strategyCard));
        for (int i = 0; i < victoryPoints; i++) {
            player.setSecretScored(SCORING_SECRETS.get(i));
        }
        return player;
    }
}
