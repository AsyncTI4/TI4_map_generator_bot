package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PersistedGameStateTest {

    @Test
    void identicalGamesHaveNoDifferencesRegardlessOfRowOrder() {
        UserEntity alice = new UserEntity("1", "alice");
        UserEntity bob = new UserEntity("2", "bob");
        GameEntity expected = game(4);
        GameEntity actual = game(4);

        PersistedGameState expectedState = PersistedGameState.of(
                expected,
                List.of(player(expected, alice, "sol"), player(expected, bob, "hacan")),
                List.of(title(expected, alice, "Kingmaker")));
        PersistedGameState actualState = PersistedGameState.of(
                actual,
                List.of(player(actual, bob, "hacan"), player(actual, alice, "sol")),
                List.of(title(actual, alice, "Kingmaker")));

        assertThat(expectedState).isEqualTo(actualState);
        assertThat(expectedState.describeDifferencesFrom(actualState)).isEmpty();
    }

    @Test
    void describesDifferingColumnsAndRows() {
        UserEntity alice = new UserEntity("1", "alice");
        GameEntity expected = game(5);
        GameEntity actual = game(4);

        PersistedGameState expectedState = PersistedGameState.of(
                expected, List.of(player(expected, alice, "sol")), List.of(title(expected, alice, "Kingmaker")));
        PersistedGameState actualState =
                PersistedGameState.of(actual, List.of(player(actual, alice, "hacan")), List.of());

        assertThat(expectedState.describeDifferencesFrom(actualState))
                .containsExactly(
                        "game columns round (expected 5, found 4)",
                        "player rows (1 missing, 1 unexpected)",
                        "title rows (1 missing, 0 unexpected)");
    }

    @Test
    void revisionMatchRequiresBothSourceAndMappingVersions() {
        GameEntity entity = game(1);
        entity.setSourceRevision("revision-a");
        entity.setMappingVersion(GameEntityMapper.MAPPING_VERSION);
        PersistedGameState state = PersistedGameState.of(entity, List.of(), List.of());
        assertThat(state.matchesRevision("revision-a")).isTrue();
        assertThat(state.matchesRevision("revision-b")).isFalse();
        assertThat(state.matchesRevision(null)).isFalse();
        assertThat(state.matchesRevision("")).isFalse();
        entity.setMappingVersion(null);
        assertThat(PersistedGameState.of(entity, List.of(), List.of()).matchesRevision("revision-a"))
                .isFalse();
        entity.setMappingVersion(GameEntityMapper.MAPPING_VERSION - 1);
        assertThat(PersistedGameState.of(entity, List.of(), List.of()).matchesRevision("revision-a"))
                .isFalse();
    }

    private static GameEntity game(int round) {
        GameEntity game = new GameEntity();
        game.setGameName("pbd1");
        game.setRound(round);
        return game;
    }

    private static PlayerEntity player(GameEntity game, UserEntity user, String faction) {
        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        player.setUser(user);
        player.setFactionName(faction);
        return player;
    }

    private static TitleEntity title(GameEntity game, UserEntity user, String title) {
        TitleEntity titleEntity = new TitleEntity();
        titleEntity.setGame(game);
        titleEntity.setUser(user);
        titleEntity.setTitle(title);
        return titleEntity;
    }
}
