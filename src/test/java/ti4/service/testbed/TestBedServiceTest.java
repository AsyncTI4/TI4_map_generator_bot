package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class TestBedServiceTest extends BaseTi4Test {

    private static final String DEV_ID = "111";

    private Game game;
    private Player developer;
    private Player nekroSeat;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("testbed-test");
        developer = seat(DEV_ID, "sol", "red");
        nekroSeat = seat(TestBedService.VIRTUAL_SEAT_ID_PREFIX + "02", "nekro", "blue");
        nekroSeat.setPrivateChannelID("seat-channel");
        nekroSeat.setCardsInfoThreadID("seat-thread");
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction + "-user");
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }

    // The developer's own player is returned untouched unless something says otherwise.
    @Test
    void defaultsToTheClickerOutsideSeatChannels() {
        assertSame(developer, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));
    }

    // Pressing a button inside a virtual seat's private channel or cards-info thread acts as that seat.
    @Test
    void seatChannelActsAsThatSeat() {
        assertSame(nekroSeat, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-channel", developer));
        assertSame(nekroSeat, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-thread", developer));
    }

    // The explicit act-as switch covers shared channels (strategy card follows in the main channel, agenda, ...).
    @Test
    void explicitActAsAppliesInSharedChannels() {
        TestBedService.setActingAs(game, DEV_ID, nekroSeat);
        assertSame(nekroSeat, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));

        TestBedService.setActingAs(game, DEV_ID, null);
        assertSame(developer, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));
    }

    // A seat channel wins over the explicit switch, so pressing in a seat's own channel is never surprising.
    @Test
    void seatChannelBeatsExplicitActAs() {
        Player solSeat = seat(TestBedService.VIRTUAL_SEAT_ID_PREFIX + "03", "hacan", "yellow");
        TestBedService.setActingAs(game, DEV_ID, solSeat);
        assertSame(nekroSeat, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-channel", developer));
    }

    // Real games are never affected: without the marker the full resolver returns the default player
    // (the global switch is off in tests, so isTestBed is false even with the marker).
    @Test
    void notATestBedMeansNoChange() {
        assertFalse(TestBedService.isMarkedAsTestBed(game));
        assertSame(developer, TestBedService.resolveActingPlayer(game, null, DEV_ID, "seat-channel", developer));

        TestBedService.markAsTestBed(game, true);
        assertTrue(TestBedService.isMarkedAsTestBed(game));
        assertSame(developer, TestBedService.resolveActingPlayer(game, null, DEV_ID, "seat-channel", developer));
    }

    @Test
    void disableClearsEveryActAs() {
        TestBedService.setActingAs(game, DEV_ID, nekroSeat);
        TestBedService.setActingAs(game, "222", nekroSeat);
        TestBedService.clearAllActingAs(game);
        assertNull(TestBedService.getActingAs(game, DEV_ID));
        assertNull(TestBedService.getActingAs(game, "222"));
    }

    @Test
    void virtualSeatsAreRecognisedByIdPrefix() {
        assertTrue(TestBedService.isVirtualSeat(nekroSeat));
        assertFalse(TestBedService.isVirtualSeat(developer));
        assertEquals("nekro", TestBedService.getActingAs(withActAs(), DEV_ID).getFaction());
    }

    private Game withActAs() {
        TestBedService.setActingAs(game, DEV_ID, nekroSeat);
        return game;
    }

    @Test
    void factionExistsForTestData() {
        assertTrue(Mapper.isValidFaction("nekro"));
    }
}
