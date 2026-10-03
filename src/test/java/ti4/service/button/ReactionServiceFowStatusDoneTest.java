package ti4.service.button;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class ReactionServiceFowStatusDoneTest extends BaseTi4Test {

    private Game game;
    private Player argent;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("fow-status-done-test");
        game.setFowMode(true);
        argent = game.addPlayer("argent-user", Mapper.getFaction("argent").getFactionName());
        argent.setFaction(game, "argent");
        argent.setColor("red");
    }

    @Test
    void nobodyIsDoneAtStartOfStatusPhase() {
        assertFalse(ReactionService.isFowStatusDone(game, argent));
    }

    @Test
    void readyPlayerIsDone() {
        game.setStoredValue("fowStatusDone", ",xxcha,argent");
        assertTrue(ReactionService.isFowStatusDone(game, argent));
    }

    // "argent" is a substring of "pi_argent"; the old contains(faction) check counted Argent as ready here.
    // Faction ids contain underscores, so an underscore-wrapped token would still match — exact list match is needed.
    @Test
    void factionIdInsideAnotherFactionIdDoesNotCount() {
        game.setStoredValue("fowStatusDone", ",pi_argent");
        assertFalse(ReactionService.isFowStatusDone(game, argent));
    }

    // Games saved before the comma format stored ready factions glued together; they must still count after deploy.
    @Test
    void legacyConcatenatedValueStillCounts() {
        game.setStoredValue("fowStatusDone", "xxchaargent");
        assertTrue(ReactionService.isFowStatusDone(game, argent));
    }
}
