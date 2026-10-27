package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ti4.game.Game;
import ti4.game.Player;

// Shared setup for the test bed tests: an in-memory game with a developer and virtual seats, no Discord.
final class TestBedFixture {

    static final String DEV_ID = "111";

    private TestBedFixture() {}

    static Game newGame(String name) {
        Game game = new Game();
        game.newGameSetup();
        game.setName(name);
        return game;
    }

    static Player developerSeat(Game game, String faction, String color) {
        return seat(game, DEV_ID, "developer", faction, color);
    }

    // Virtual seats get the reserved id prefix and the TestSeatN user name, exactly like /testbed apply makes them.
    static Player virtualSeat(Game game, int number, String faction, String color) {
        return seat(
                game,
                TestBedService.VIRTUAL_SEAT_ID_PREFIX + String.format("%02d", number),
                "TestSeat" + number,
                faction,
                color);
    }

    private static Player seat(Game game, String userId, String userName, String faction, String color) {
        Player player = game.addPlayer(userId, userName);
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }

    static void assertContains(List<String> errors, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(
                    errors.stream().anyMatch(error -> error.contains(fragment)),
                    "`" + fragment + "` not in:\n" + String.join("\n", errors));
        }
    }
}
