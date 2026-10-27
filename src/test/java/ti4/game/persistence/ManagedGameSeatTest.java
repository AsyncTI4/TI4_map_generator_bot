package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.statistics.UserGameInfoService;
import ti4.testUtils.BaseTi4Test;

class ManagedGameSeatTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("sol", "hacan", "xxcha", "letnev", "jolnar", "yin");
    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "black");

    @Test
    void realAndEliminatedCountMatchesTheFullGame() {
        Game game = newGame();
        String realUser = seat(game, 0).getUserID();
        Player eliminated = seat(game, 1);
        eliminated.setEliminated(true);
        Player npc = seat(game, 2);
        npc.setNpc(true);
        String spectator = game.addPlayer(uniqueName("spectator"), "Spectator").getUserID();

        ManagedGame managedGame = new ManagedGame(game);

        assertThat(managedGame.countRealAndEliminatedPlayers())
                .isEqualTo(game.getRealAndEliminatedPlayers().size())
                .isEqualTo(3);
        assertThat(managedGame.isRealOrEliminatedPlayer(realUser)).isTrue();
        assertThat(managedGame.isRealOrEliminatedPlayer(eliminated.getUserID())).isTrue();
        // NPCs count toward the join limit table size but are not "real" for presence and activity checks.
        assertThat(managedGame.isRealOrEliminatedPlayer(npc.getUserID())).isTrue();
        assertThat(managedGame.isRealOrEliminatedPlayer(spectator)).isFalse();
        assertThat(managedGame.isRealOrEliminatedPlayer(uniqueName("stranger"))).isFalse();
        // An eliminated seat keeps its faction and color, so it still counts as real; only the NPC drops out.
        assertThat(managedGame.getRealPlayers())
                .extracting(ManagedPlayer::getId)
                .containsExactlyInAnyOrder(realUser, eliminated.getUserID());
    }

    @Test
    void joinLimitCountsNeverLoadGamesFromDisk() {
        String userId = uniqueName("join-limit-user");
        Game ongoing = gameWithUserAndTwoOthers(userId);
        Game twoSeatOngoing = newGame();
        seat(twoSeatOngoing, 0, userId);
        seat(twoSeatOngoing, 1);
        Game completed = gameWithUserAndTwoOthers(userId);
        completed.setHasEnded(true);
        completed.recordWinner(completed.getPlayer(userId));
        Game abandoned = gameWithUserAndTwoOthers(userId);
        abandoned.setHasEnded(true);

        new ManagedGame(ongoing);
        new ManagedGame(twoSeatOngoing);
        new ManagedGame(completed);
        ManagedPlayer managedPlayer = new ManagedGame(abandoned).getPlayer(userId);

        try (MockedStatic<GameLoadService> gameLoadService = mockStatic(GameLoadService.class)) {
            assertThat(UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer))
                    .isEqualTo(1);
            assertThat(UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer))
                    .isEqualTo(1);
            gameLoadService.verify(() -> GameLoadService.load(anyString()), never());
        }
    }

    private static Game gameWithUserAndTwoOthers(String userId) {
        Game game = newGame();
        seat(game, 0, userId);
        seat(game, 1);
        seat(game, 2);
        return game;
    }

    private static Player seat(Game game, int index) {
        return seat(game, index, uniqueName("player"));
    }

    private static Player seat(Game game, int index, String userId) {
        Player player = game.addPlayer(userId, "Player " + index);
        player.setFaction(game, FACTIONS.get(index));
        player.setColor(COLORS.get(index));
        return player;
    }

    private static Game newGame() {
        Game game = new Game();
        game.setName(uniqueName("managed-game-seat"));
        game.setLastModifiedDate(0);
        return game;
    }

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
