package ti4.cron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class SabotageAutoReactCronTest extends BaseTi4Test {

    @Test
    void testIsEligibleForAutoPass_EmptyHand() {
        Game game = new Game();
        Player player = createPlayer(game);

        assertTrue(SabotageAutoReactCron.isEligibleForAutoPass(player, game));
    }

    @Test
    void testIsEligibleForAutoPass_ActivePlayerMustReactManually() {
        Game game = new Game();
        Player player = createPlayer(game);
        game.setActivePlayerID(player.getUserID());

        assertTrue(player.isActivePlayer());
        assertFalse(SabotageAutoReactCron.isEligibleForAutoPass(player, game));
    }

    @Test
    void testIsEligibleForAutoPass_HoldingSabotage() {
        Game game = new Game();
        Player player = createPlayer(game);
        player.setActionCard("sabo1");

        assertFalse(SabotageAutoReactCron.isEligibleForAutoPass(player, game));
    }

    @Test
    void testIsEligibleForAutoPass_AutoPassDisabled() {
        Game game = new Game();
        Player player = createPlayer(game);
        player.setAutoSaboPassMedian(0);

        assertFalse(SabotageAutoReactCron.isEligibleForAutoPass(player, game));
    }

    private static Player createPlayer(Game game) {
        game.setActionCards(Mapper.getShuffledDeck("action_cards_pok"));
        Player player = game.addPlayer("101", "testUser");
        player.setFaction("winnu");
        player.setColor("red");
        player.setAutoSaboPassMedian(1);
        return player;
    }
}
