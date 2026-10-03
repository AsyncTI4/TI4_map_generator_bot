package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class GameEntityMapperTest extends BaseTi4Test {

    @Test
    void replacedPlayerUsesStatsTrackedUserNameFromTheGameFile() {
        Game game = new Game();
        game.setName("mapper-replaced-player");
        Player replacement = game.addPlayer("replacement-id", "Replacement");
        replacement.setFaction("sol");
        replacement.setColor("red");
        replacement.setStatsTrackedUserID("original-id");
        replacement.setStatsTrackedUserName("Original");

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        PlayerEntity playerEntity = snapshot.game().getPlayers().getFirst();
        assertThat(playerEntity.isReplaced()).isTrue();
        assertThat(playerEntity.getUser().getId()).isEqualTo("original-id");
        assertThat(playerEntity.getUser().getName()).isEqualTo("Original");
        // Only users a player or title row points at are written, otherwise the nightly reconciler would
        // delete the replacement's unreferenced user row and report it every night.
        assertThat(snapshot.users()).extracting(UserEntity::getId).containsExactly("original-id");
    }

    @Test
    void replacedPlayerWithNoKnownNameGetsPlaceholder() {
        Game game = new Game();
        game.setName("mapper-unknown-player");
        Player replacement = game.addPlayer("replacement-id", "Replacement");
        replacement.setFaction("sol");
        replacement.setColor("red");
        replacement.setStatsTrackedUserID("original-id");
        replacement.setStatsTrackedUserName(null);

        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        UserEntity user = snapshot.game().getPlayers().getFirst().getUser();
        // The placeholder lets GameEntityPersistenceService keep any real name already stored for this user.
        assertThat(GameEntityMapper.hasUnknownName(user)).isTrue();
    }
}
