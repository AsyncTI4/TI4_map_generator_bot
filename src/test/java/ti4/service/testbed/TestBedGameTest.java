package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ti4.service.testbed.TestBedFixture.DEV_ID;

import java.util.List;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.model.TestBedScript.Shortcut;
import ti4.service.testbed.TestBedPanelService.Tool;
import ti4.service.testbed.TestBedResetService.ResetResult;
import ti4.testUtils.BaseTi4Test;

// Behaviour on an in-memory game: who a developer acts as, reset, the state paths scripts read, and the panel.
class TestBedGameTest extends BaseTi4Test {

    private static final List<String> FACTIONS =
            List.of("sol", "nekro", "hacan", "jolnar", "letnev", "naalu", "arborec", "muaat", "yin");
    private static final List<String> COLORS =
            List.of("red", "blue", "green", "yellow", "purple", "orange", "pink", "black", "brown");

    private Game game;
    private Player developer;
    private Player nekro;

    @BeforeEach
    void setUp() {
        game = TestBedFixture.newGame("testbed-game");
        developer = TestBedFixture.developerSeat(game, "sol", "red");
        nekro = TestBedFixture.virtualSeat(game, 1, "nekro", "blue");
        nekro.setPrivateChannelID("seat-channel");
        nekro.setCardsInfoThreadID("seat-thread");
    }

    // The order is: the seat whose channel you are in, then the explicit act-as seat, then yourself.
    @Test
    void developerActsAsTheRightSeat() {
        Player hacan = TestBedFixture.virtualSeat(game, 2, "hacan", "green");
        assertSame(developer, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));
        assertSame(nekro, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-channel", developer));
        assertSame(nekro, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-thread", developer));

        TestBedService.setActingAs(game, DEV_ID, hacan);
        assertSame(hacan, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));
        assertSame(nekro, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-channel", developer));

        TestBedService.setActingAs(game, DEV_ID, null);
        assertSame(developer, TestBedService.resolveForDeveloper(game, DEV_ID, "main-channel", developer));
    }

    // Real games must never change: the resolver is a no-op without the global switch (off in tests), even when
    // the game carries the marker, and for anyone who is not a developer.
    @Test
    void realGamesAreUnaffected() {
        assertSame(developer, TestBedService.resolveActingPlayer(game, null, DEV_ID, "seat-channel", developer));
        TestBedService.markAsTestBed(game, true);
        assertTrue(TestBedService.isMarkedAsTestBed(game));
        assertFalse(TestBedService.isTestBed(game));
        assertSame(developer, TestBedService.resolveActingPlayer(game, null, DEV_ID, "seat-channel", developer));
    }

    @Test
    void actAsStateIsPerUserAndClearable() {
        assertTrue(TestBedService.isVirtualSeat(nekro));
        assertFalse(TestBedService.isVirtualSeat(developer));
        TestBedService.setActingAs(game, DEV_ID, nekro);
        TestBedService.setActingAs(game, "222", nekro);
        assertEquals("nekro", TestBedService.getActingAs(game, DEV_ID).getFaction());

        TestBedService.clearAllActingAs(game);
        assertNull(TestBedService.getActingAs(game, DEV_ID));
        assertNull(TestBedService.getActingAs(game, "222"));
    }

    // Reset removes virtual seats, unseats the developer (keeping their own thread), restores the decks and clears
    // every test bed marker, so `/testbed apply` can run again in the same game.
    @Test
    void resetReturnsTheGameToAFreshState() {
        developer.setCardsInfoThreadID("dev-thread");
        TestBedService.markAsTestBed(game, true);
        TestBedChannelService.recordCreatedChannel(game, "seat-thread");
        game.setTile(new Tile("19", "101"));
        game.setSpeakerUserID(DEV_ID);
        game.drawActionCard(nekro.getUserID(), 3);
        TestBedService.setActingAs(game, DEV_ID, nekro);
        int fullDeck = Mapper.getDeck(game.getAcDeckID()).getNewShuffledDeck().size();

        ResetResult result = TestBedResetService.reset(game);

        assertEquals(new ResetResult(1, 1, 1), result);
        assertNull(game.getPlayer(nekro.getUserID()));
        Player unseated = game.getPlayer(DEV_ID);
        assertNotNull(unseated);
        assertFalse(unseated.isRealPlayer());
        assertEquals("dev-thread", unseated.getCardsInfoThreadID());
        assertEquals(fullDeck, game.getActionCards().size());
        assertTrue(game.getTileMap().isEmpty());
        assertEquals("", game.getSpeakerUserID());
        assertFalse(TestBedService.isMarkedAsTestBed(game));
        assertNull(TestBedService.getActingAs(game, DEV_ID));
        assertTrue(TestBedChannelService.createdChannelIds(game).isEmpty());
    }

    // Every advertised state path must be implemented; adding a field to the list without a resolver fails here.
    @Test
    void everyStatePathResolves() {
        for (String field : TestBedStateResolver.SEAT_FIELDS) {
            assertFalse(resolve("nekro." + field).startsWith("<"), "seat field " + field);
        }
        for (String field : TestBedStateResolver.GAME_FIELDS) {
            assertFalse(resolve("game." + field).startsWith("<"), "game field " + field);
        }
    }

    @Test
    void statePathsReadTheGame() {
        nekro.setTg(4);
        nekro.setTacticalCC(3);
        nekro.setFleetCC(2);
        nekro.setStrategicCC(1);
        nekro.addSC(5);
        nekro.addSC(2);
        game.setSpeakerUserID(nekro.getUserID());
        game.setStoredValue("factionsInCombat", "letnev_nekro");

        assertEquals("4", resolve("nekro.tg"));
        assertEquals("3/2/1", resolve("nekro.ccs"));
        assertEquals("2,5", resolve("nekro.scs"));
        assertEquals("nekro", resolve("game.speaker"));
        assertEquals("letnev_nekro", resolve("stored:factionsInCombat"));
        assertNull(TestBedStateResolver.validatePath("stored:anything"));
        assertNotNull(TestBedStateResolver.validatePath("nekro.mood"));
        assertNotNull(TestBedStateResolver.validatePath("game.weather"));
        assertNotNull(TestBedStateResolver.validatePath("nekro"));
    }

    private String resolve(String path) {
        return TestBedStateResolver.resolve(game, path, name -> game.getPlayerFromColorOrFaction(name));
    }

    // Placeholders turn card ids into the seat's current hand numbers and seat names into factions or colors, so a
    // script can press `ac_play_from_hand_<n>` without knowing <n> in advance.
    @Test
    void placeholdersResolveHandNumbersAndSeats() {
        game.drawSpecificActionCard("sabo1", nekro.getUserID());
        int number = nekro.getActionCards().get("sabo1");

        TestBedPlaceholders.Resolution resolved = TestBedPlaceholders.resolve(
                "ac_play_from_hand_{ac:sabo1} by {seat1.faction} in {nekro.color}",
                nekro,
                name -> "seat1".equals(name) ? nekro : game.getPlayerFromColorOrFaction(name));
        assertEquals(List.of(), resolved.problems());
        assertEquals("ac_play_from_hand_" + number + " by nekro in blue", resolved.text());

        assertEquals(
                "resolvePNPlay_blue_sftt",
                TestBedPlaceholders.resolve(
                                "resolvePNPlay_{nekro.color}_sftt", nekro, game::getPlayerFromColorOrFaction)
                        .text());
        assertEquals(
                List.of("nekro has no `sabo2` in hand; hand: [sabo1]"),
                TestBedPlaceholders.resolve("{ac:sabo2}", nekro, game::getPlayerFromColorOrFaction)
                        .problems());
        assertEquals(
                List.of("no seat `arborec`"),
                TestBedPlaceholders.resolve("{arborec.color}", nekro, game::getPlayerFromColorOrFaction)
                        .problems());
    }

    // At the 8-seat maximum (plus the developer) the panel must still fit Discord's 5 rows of 5 buttons, with ids
    // and labels inside Discord's limits; so must the shortcut list however many shortcuts a preset declares.
    @Test
    void panelsFitDiscordLimits() {
        IntStream.range(2, FACTIONS.size())
                .forEach(i -> TestBedFixture.virtualSeat(game, i, FACTIONS.get(i), COLORS.get(i)));
        assertWithinLimits(TestBedPanelService.components(game, nekro));

        Shortcut shortcut = new Shortcut();
        shortcut.setLabel("A shortcut label that is rather long to see the truncation stay within limits ok");
        TestBedShortcuts.store(
                game, IntStream.range(0, 30).mapToObj(i -> shortcut).toList());
        assertWithinLimits(TestBedPanelService.shortcutComponents(game));
    }

    private static void assertWithinLimits(List<ActionRow> rows) {
        assertTrue(rows.size() <= 5, "rows: " + rows.size());
        for (ActionRow row : rows) {
            assertTrue(
                    row.getButtons().size() <= 5,
                    "buttons in a row: " + row.getButtons().size());
            for (Button button : row.getButtons()) {
                assertTrue(button.getCustomId().length() <= 100, button.getCustomId());
                assertTrue(button.getLabel().length() <= 80, button.getLabel());
            }
        }
    }

    @Test
    void panelHighlightsTheActingSeatAndToolsChangeIt() {
        Button nekroButton = TestBedPanelService.components(game, nekro).getFirst().getButtons().stream()
                .filter(button -> button.getCustomId().equals(TestBedPanelService.ACT_AS + "nekro"))
                .findFirst()
                .orElseThrow();
        assertEquals(ButtonStyle.SUCCESS, nekroButton.getStyle());

        nekro.setTg(2);
        nekro.setTacticalCC(3);
        nekro.setStrategicCC(1);
        TestBedPanelService.applyTool(Tool.tg, nekro);
        TestBedPanelService.applyTool(Tool.tactic, nekro);
        TestBedPanelService.applyTool(Tool.strategy, nekro);
        assertEquals(3, nekro.getTg());
        assertEquals(4, nekro.getTacticalCC());
        assertEquals(2, nekro.getStrategicCC());
    }
}
