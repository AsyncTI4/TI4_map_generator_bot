package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void revertBlackSpectrumPlotsStripsOnlyTheBlackSpectrumDuplicates() {
        Game game = new Game();
        Player player = new Player("user-id", "user/name", game);
        player.setColor("red");
        game.setPlayers(new LinkedHashMap<>(Map.of("user-id", player)));

        player.setPlotCard("seethe");
        player.setPlotCard("bsp_seethe");
        player.setPlotCard("assail");
        player.setPlotCard("bsp_assail");

        boolean changed = RunAgainstAllGames.revertBlackSpectrumPlots(game);

        assertThat(changed).isTrue();
        assertThat(player.getPlotCardsRaw()).containsKey("seethe").containsKey("assail");
        assertThat(player.getPlotCardsRaw()).doesNotContainKey("bsp_seethe").doesNotContainKey("bsp_assail");

        // Running again makes no further changes
        assertThat(RunAgainstAllGames.revertBlackSpectrumPlots(game)).isFalse();
    }
}
