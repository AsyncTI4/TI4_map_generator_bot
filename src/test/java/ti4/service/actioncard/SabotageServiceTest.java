package ti4.service.actioncard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.testUtils.BaseTi4Test;

class SabotageServiceTest extends BaseTi4Test {

    @Test
    void testCouldFeasiblySabotage_EmptyHand_SabotageOnGarbozia() {
        Game game = new Game();
        Player player = createPlayer(game);
        game.getDiscardACStatus().put("sabo1", ACStatus.garbozia);
        player.addPlanet("garbozia");

        assertTrue(player.getActionCards().isEmpty());
        assertTrue(SabotageService.couldFeasiblySabotage(player, game));
    }

    @Test
    void testCouldFeasiblySabotage_EmptyHand_SabotageOnGarboziaButPlanetNotControlled() {
        Game game = new Game();
        Player player = createPlayer(game);
        game.getDiscardACStatus().put("sabo1", ACStatus.garbozia);

        assertFalse(SabotageService.couldFeasiblySabotage(player, game));
    }

    @Test
    void testCouldFeasiblySabotage_SabotageOnGarboziaButElectedByPoliticalCensure() {
        Game game = new Game();
        Player player = createPlayer(game);
        game.getDiscardACStatus().put("sabo1", ACStatus.garbozia);
        player.addPlanet("garbozia");
        game.addLaw("censure", player.getFaction());

        assertFalse(SabotageService.couldFeasiblySabotage(player, game));
    }

    private static Player createPlayer(Game game) {
        Player player = game.addPlayer("101", "testUser");
        player.setFaction("winnu");
        player.setColor("red");
        return player;
    }
}
