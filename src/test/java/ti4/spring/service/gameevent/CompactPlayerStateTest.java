package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;

class CompactPlayerStateTest {

    @Test
    void firstSnapshotOfAnEmptyGameCarriesVersionAndEmptyRevealedList() {
        CompactPlayerState.Snapshot snapshot = CompactPlayerState.snapshot(new Game());

        assertThat(snapshot.full()).isEqualTo("{\"v\":3,\"speaker\":null,\"revealed\":[],\"players\":{}}");
        assertThat(snapshot.delta()).isEqualTo("{\"v\":3,\"revealed\":[]}");
    }

    @Test
    void unchangedStateProducesNoDelta() {
        Game game = new Game();
        game.setLastPlayerStateJson(CompactPlayerState.snapshot(game).full());

        assertThat(CompactPlayerState.snapshot(game).delta()).isNull();
    }

    @Test
    void onlyChangedPartsAppearInTheDelta() {
        Game game = new Game();
        game.addCustomPO("First", 1);
        game.setLastPlayerStateJson(CompactPlayerState.snapshot(game).full());
        game.addCustomPO("Second", 1);

        CompactPlayerState.Snapshot snapshot = CompactPlayerState.snapshot(game);

        assertThat(snapshot.delta()).isEqualTo("{\"v\":3,\"revealed\":[\"First\",\"Second\"]}");
        assertThat(snapshot.full()).contains("\"First\",\"Second\"");
    }
}
