package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class CustomPONameForTest extends BaseTi4Test {

    private static Player seat(Game game, String userID, String faction, String color) {
        Player player = game.addPlayer(userID, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    private static void score(Game game, Player player, String name, int vp) {
        game.scorePublicObjective(player.getUserID(), game.addCustomPO(name, vp));
    }

    @Test
    void nonFogKeepsFactionName() {
        Game game = new Game();
        Player mentak = seat(game, "user-1", "mentak", "red");
        assertThat(game.customPONameFor(mentak, "Total War VPs")).isEqualTo("Total War VPs (mentak)");
    }

    @Test
    void fogNamesAreNeutralAndNumberedPerPlayer() {
        Game game = new Game();
        game.setFowMode(true);
        Player mentak = seat(game, "user-1", "mentak", "red");
        Player arborec = seat(game, "user-2", "arborec", "plum");

        assertThat(game.customPONameFor(mentak, "Total War VPs")).isEqualTo("Total War VPs");
        score(game, mentak, "Total War VPs", 1);
        assertThat(game.customPONameFor(arborec, "Total War VPs")).isEqualTo("Total War VPs 2");
        score(game, arborec, "Total War VPs 2", 1);
        assertThat(game.customPONameFor(mentak, "Total War VPs")).isEqualTo("Total War VPs");
        assertThat(game.customPONameFor(arborec, "Total War VPs")).isEqualTo("Total War VPs 2");
    }

    // Faction- and colour-named POs from before the fix lose their identity on the owner's next score, keeping VP and
    // scorer.
    @Test
    void legacyFactionAndColorPOsAreAnonymizedOnNextScore() {
        Game game = new Game();
        Player mentak = seat(game, "user-1", "mentak", "red");
        Player arborec = seat(game, "user-2", "arborec", "plum");
        score(game, mentak, "Total War VPs (mentak)", 3);
        score(game, arborec, "Total War VPs (plum)", 1);
        game.setFowMode(true);

        assertThat(game.customPONameFor(mentak, "Total War VPs")).isEqualTo("Total War VPs");
        assertThat(game.customPONameFor(arborec, "Total War VPs")).isEqualTo("Total War VPs 2");

        assertThat(game.getCustomPublicVP())
                .containsOnlyKeys("Total War VPs", "Total War VPs 2")
                .containsEntry("Total War VPs", 3)
                .containsEntry("Total War VPs 2", 1);
        assertThat(game.getScoredPublicObjectives().get("Total War VPs")).containsExactly("user-1");
        assertThat(game.getScoredPublicObjectives().get("Total War VPs 2")).containsExactly("user-2");
    }
}
