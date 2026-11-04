package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class CustomPONameForTest extends BaseTi4Test {

    private static Player seat(Game game) {
        Player player = game.addPlayer("user-1", "mentak");
        player.setFaction("mentak");
        player.setColor("red");
        return player;
    }

    @Test
    void nonFogKeepsFactionName() {
        Game game = new Game();
        assertThat(game.customPONameFor(seat(game), "Total War VPs")).isEqualTo("Total War VPs (mentak)");
    }

    @Test
    void fogUsesColorAndCarriesOverLegacyFactionPO() {
        Game game = new Game();
        Player player = seat(game);
        game.scorePublicObjective(player.getUserID(), game.addCustomPO("Total War VPs (mentak)", 2));
        game.setFowMode(true);

        String name = game.customPONameFor(player, "Total War VPs");

        assertThat(name).isEqualTo("Total War VPs (red)");
        assertThat(game.getCustomPublicVP()).containsEntry(name, 2).doesNotContainKey("Total War VPs (mentak)");
        assertThat(game.getRevealedPublicObjectives()).containsKey(name).doesNotContainKey("Total War VPs (mentak)");
        assertThat(game.getScoredPublicObjectives().get(name)).containsExactly("user-1");
    }
}
