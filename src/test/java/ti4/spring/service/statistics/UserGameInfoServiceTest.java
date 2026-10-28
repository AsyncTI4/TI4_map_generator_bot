package ti4.spring.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;
import ti4.spring.service.persistence.UserEntity;

class UserGameInfoServiceTest {

    private static final UserEntity ALICE = new UserEntity("alice", "Alice");
    private static final UserEntity BOB = new UserEntity("bob", "Bob");

    @Test
    void batchLookupMatchesPerUserLookup() {
        List<PlayerEntity> alicePlayers =
                List.of(seat(ALICE, "a1", 20), seat(ALICE, "a2", 9), seat(ALICE, "a3", 14), seat(ALICE, "a4", 0));
        List<PlayerEntity> bobPlayers = List.of(seat(BOB, "b1", 30));
        List<PlayerEntity> everyone = new ArrayList<>(alicePlayers);
        everyone.addAll(bobPlayers);

        PlayerEntityRepository repository = mock(PlayerEntityRepository.class);
        when(repository.findAllWithUsersAndSixPlayerCompletedGames()).thenReturn(everyone);
        when(repository.findAllWithGamesByUserIdEquals("alice")).thenReturn(alicePlayers);
        when(repository.findAllWithGamesByUserIdEquals("bob")).thenReturn(bobPlayers);
        UserGameInfoService service = new UserGameInfoService(repository);

        Map<String, List<Integer>> byUserId = service.getThreeFastestDaysToComplete6PlayerGamesByUserId();

        // Same-day completions (0 days) are dropped, the rest sorted fastest first.
        assertThat(byUserId.get("alice")).containsExactly(9, 14, 20);
        assertThat(byUserId.get("alice")).isEqualTo(service.getUsersThreeFastestDaysToComplete6PlayerGames("alice"));
        assertThat(byUserId.get("bob")).isEqualTo(service.getUsersThreeFastestDaysToComplete6PlayerGames("bob"));
        assertThat(byUserId).containsOnlyKeys("alice", "bob");
    }

    private static PlayerEntity seat(UserEntity user, String gameName, int daysToComplete) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setPlayerCount(6);
        game.setCompleted(true);
        game.setCreationEpochMilliseconds(0);
        game.setEndedEpochMilliseconds(Duration.ofDays(daysToComplete).toMillis());

        PlayerEntity player = new PlayerEntity();
        player.setUser(user);
        player.setGame(game);
        return player;
    }
}
