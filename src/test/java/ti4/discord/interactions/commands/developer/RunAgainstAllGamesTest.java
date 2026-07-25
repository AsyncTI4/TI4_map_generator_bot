package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

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

    @Test
    void revertOtherBlackSpectrumComponentsSwapsUnitsLeadersAndTechsBackToWhatTheyReplaced() {
        Game game = new Game();
        Player player = new Player("user-id", "user/name", game);
        player.setColor("red");
        game.setPlayers(new LinkedHashMap<>(Map.of("user-id", player)));

        player.setUnitsOwned(new HashSet<>(List.of("bsp_bastion_spacedock2", "cruiser")));
        player.addLeader("bsp_yinhero");
        player.getTechs().add("bsp_st");
        player.getTechs().add("cl2");

        boolean changed = RunAgainstAllGames.revertOtherBlackSpectrumComponents(game);

        assertThat(changed).isTrue();
        assertThat(player.getUnitsOwned()).contains("bastion_spacedock2", "cruiser");
        assertThat(player.getUnitsOwned()).doesNotContain("bsp_bastion_spacedock2");
        assertThat(player.getLeaders().stream().map(Leader::getId)).contains("yinhero");
        assertThat(player.getLeaders().stream().map(Leader::getId)).doesNotContain("bsp_yinhero");
        assertThat(player.getTechs()).contains("st", "cl2");
        assertThat(player.getTechs()).doesNotContain("bsp_st");

        // Running again makes no further changes
        assertThat(RunAgainstAllGames.revertOtherBlackSpectrumComponents(game)).isFalse();
    }
}
