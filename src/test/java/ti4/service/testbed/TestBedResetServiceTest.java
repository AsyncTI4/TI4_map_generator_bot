package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.service.testbed.TestBedResetService.ResetResult;
import ti4.testUtils.BaseTi4Test;

class TestBedResetServiceTest extends BaseTi4Test {

    private static final String DEV_ID = "111";
    private static final String SEAT_ID = TestBedService.VIRTUAL_SEAT_ID_PREFIX + "01";

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("testbed-reset");
        TestBedService.markAsTestBed(game, true);

        Player developer = seat(DEV_ID, "sol", "red");
        developer.setCardsInfoThreadID("dev-thread");
        Player virtualSeat = seat(SEAT_ID, "nekro", "blue");
        virtualSeat.setCardsInfoThreadID("seat-thread");
        TestBedChannelService.recordCreatedChannel(game, "seat-thread");

        game.setTile(new Tile("19", "101"));
        game.setSpeakerUserID(DEV_ID);
        game.drawActionCard(SEAT_ID, 3);
        TestBedService.setActingAs(game, DEV_ID, virtualSeat);
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction + "-user");
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }

    // Virtual seats disappear; the developer stays in the game but unseated, so `/testbed apply` can run again.
    @Test
    void removesVirtualSeatsAndUnseatsTheDeveloper() {
        ResetResult result = TestBedResetService.reset(game);

        assertEquals(1, result.removedSeats());
        assertEquals(1, result.resetSeats());
        assertNull(game.getPlayer(SEAT_ID));
        Player developer = game.getPlayer(DEV_ID);
        assertNotNull(developer);
        assertFalse(developer.isRealPlayer());
        assertTrue(game.getRealPlayers().isEmpty());
    }

    // The developer's own cards-info thread was not created by the test bed, so it is kept for the next apply.
    @Test
    void keepsTheDevelopersOwnThread() {
        TestBedResetService.reset(game);
        assertEquals("dev-thread", game.getPlayer(DEV_ID).getCardsInfoThreadID());
    }

    // Cards drawn into removed hands are back in a full deck, and the map, speaker and markers are cleared.
    @Test
    void restoresDecksAndClearsGameState() {
        int fullDeck = Mapper.getDeck(game.getAcDeckID()).getNewShuffledDeck().size();

        TestBedResetService.reset(game);

        assertEquals(fullDeck, game.getActionCards().size());
        assertTrue(game.getTileMap().isEmpty());
        assertEquals("", game.getSpeakerUserID());
        assertFalse(TestBedService.isMarkedAsTestBed(game));
        assertNull(TestBedService.getActingAs(game, DEV_ID));
        assertTrue(TestBedChannelService.createdChannelIds(game).isEmpty());
    }
}
