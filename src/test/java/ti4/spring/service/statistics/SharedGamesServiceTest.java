package ti4.spring.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;
import ti4.spring.service.persistence.UserEntity;

class SharedGamesServiceTest {

    private PlayerEntityRepository playerEntityRepository;
    private SharedGamesService service;

    @BeforeEach
    void beforeEach() {
        playerEntityRepository = Mockito.mock(PlayerEntityRepository.class);
        service = new SharedGamesService(playerEntityRepository);
    }

    @Test
    void getSharedGameCountsCountsOnlyGamesSharedWithJoiningPlayer() {
        when(playerEntityRepository.findAllWithUsersAndGamesByUserIdIn(List.of("joiner", "alpha", "beta")))
                .thenReturn(List.of(
                        player("joiner", "g1", false),
                        player("alpha", "g1", false),
                        player("joiner", "g2", false),
                        player("beta", "g2", false),
                        player("joiner", "g3", false),
                        player("alpha", "g3", true),
                        player("alpha", "g4", false),
                        player("beta", "g4", false)));

        assertThat(service.getSharedGameCounts("joiner", List.of("alpha", "beta")))
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("alpha", 1),
                        org.assertj.core.api.Assertions.entry("beta", 1));
    }

    @Test
    void getSharedGameCountsReturnsEmptyWhenThereAreNoOtherPlayersToCheck() {
        assertThat(service.getSharedGameCounts("joiner", List.of())).isEmpty();
    }

    private static PlayerEntity player(String userId, String gameName, boolean replaced) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setName(userId);

        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        player.setUser(user);
        player.setReplaced(replaced);
        return player;
    }
}
