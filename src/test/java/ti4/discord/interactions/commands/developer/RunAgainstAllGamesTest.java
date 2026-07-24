package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class RunAgainstAllGamesTest {

    @Test
    void removeBlackSpectrumGenericPNsStripsOnlyTheBlackSpectrumDuplicates() {
        Game game = new Game();
        Player player = new Player("user-id", "user/name", game);
        player.setColor("red");
        game.setPlayers(new LinkedHashMap<>(Map.of("user-id", player)));

        player.setPromissoryNote("red_ps");
        player.setPromissoryNote("red_bsp_ps");
        player.setPromissoryNote("red_sftt");
        player.setPromissoryNote("red_bsp_sftt");
        player.setPromissoryNotesOwned(new HashSet<>(List.of("red_ps", "red_bsp_ps", "red_sftt", "red_bsp_sftt")));

        boolean changed = RunAgainstAllGames.removeBlackSpectrumGenericPNs(game);

        assertThat(changed).isTrue();
        assertThat(player.getPromissoryNotes()).containsKey("red_ps").containsKey("red_sftt");
        assertThat(player.getPromissoryNotes()).doesNotContainKey("red_bsp_ps").doesNotContainKey("red_bsp_sftt");
        assertThat(player.getPromissoryNotesOwned()).contains("red_ps", "red_sftt");
        assertThat(player.getPromissoryNotesOwned()).doesNotContain("red_bsp_ps", "red_bsp_sftt");

        // Running again makes no further changes
        assertThat(RunAgainstAllGames.removeBlackSpectrumGenericPNs(game)).isFalse();
    }
}
