package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ti4.service.testbed.TestBedFixture.DEV_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.MapSegment;
import ti4.image.Mapper;
import ti4.model.TestBedPreset;
import ti4.model.TestBedScript.Shortcut;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.testbed.TestBedPanelService.PageRef;
import ti4.service.testbed.TestBedResetService.ResetResult;
import ti4.service.testbed.TestBedShortcuts.ButtonGroup;
import ti4.service.testbed.TestBedTurnButtons.TurnButtons;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;
import ti4.testUtils.BaseTi4Test;

// Behaviour on an in-memory game: who a developer acts as, the safety gates, reset, components and the panel.
class TestBedGameTest extends BaseTi4Test {

    private static final List<String> FACTIONS =
            List.of("sol", "nekro", "hacan", "jolnar", "letnev", "naalu", "arborec", "muaat");
    private static final List<String> COLORS =
            List.of("red", "blue", "green", "yellow", "purple", "orange", "pink", "black");

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

    // Order: the seat whose channel you are in, then the act-as seat (or the active one with Follow Turn), then you.
    // A script press must restore Follow Turn exactly, not pin it to whoever happened to be active.
    @Test
    void actAsResolvesTheRightSeat() {
        Player hacan = TestBedFixture.virtualSeat(game, 2, "hacan", "green");
        assertSame(developer, TestBedService.resolveForDeveloper(game, DEV_ID, "main", developer));
        assertSame(nekro, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-thread", developer));

        TestBedService.setActingAs(game, DEV_ID, hacan);
        assertSame(hacan, TestBedService.resolveForDeveloper(game, DEV_ID, "main", developer));
        assertSame(nekro, TestBedService.resolveForDeveloper(game, DEV_ID, "seat-channel", developer));

        TestBedService.followTurn(game, DEV_ID);
        game.setActivePlayerID(hacan.getUserID());
        assertSame(hacan, TestBedService.resolveForDeveloper(game, DEV_ID, "main", developer));
        String saved = TestBedService.rawActingAs(game, DEV_ID);
        TestBedService.setActingAs(game, DEV_ID, nekro);
        TestBedService.restoreActingAs(game, DEV_ID, saved);
        assertTrue(TestBedService.isFollowingTurn(game, DEV_ID));

        TestBedService.clearAllActingAs(game);
        assertNull(TestBedService.getActingAs(game, DEV_ID));
    }

    // Real games never change: no-op without the switch (off in tests) even with the marker; bots never block;
    // real-player mode keeps act-as to buttons; the switch accepts `true` as text and never throws.
    @Test
    void safetyGatesHold() {
        TestBedService.markAsTestBed(game, true);
        assertFalse(TestBedService.isTestBed(game));
        assertSame(developer, TestBedService.resolveActingPlayer(game, null, DEV_ID, "seat-channel", developer));

        TestBedService.allowRealPlayers(game);
        assertTrue(TestBedService.actAsApplies(game, true));
        assertFalse(TestBedService.actAsApplies(game, false));
        TestBedService.markAsTestBed(game, false);
        assertFalse(TestBedService.allowsRealPlayers(game));

        Player diceBot = game.addPlayer("555", "Dicecord");
        User botUser = mock(User.class);
        when(botUser.isBot()).thenReturn(true);
        Member botMember = mock(Member.class);
        when(botMember.getUser()).thenReturn(botUser);
        Guild guild = mock(Guild.class);
        when(guild.getMemberById("555")).thenReturn(botMember);
        assertNull(TestBedService.findNonDeveloper(guild, List.of(diceBot, nekro)));
        assertSame(developer, TestBedService.findNonDeveloper(guild, List.of(diceBot, developer)));

        String key = ImplementedSettings.TESTBED_ENABLED.toString();
        try {
            GlobalSettings.setSetting(key, "true");
            assertTrue(TestBedService.isEnabled());
            GlobalSettings.setSetting(key, "no");
            assertFalse(TestBedService.isEnabled());
        } finally {
            GlobalSettings.setSetting(key, false);
        }
    }

    // Without a snapshot, reset rebuilds: virtual seats go, the developer is unseated but keeps their thread, decks
    // and played cards are restored, markers cleared. Channels that already existed are never recorded for deletion.
    @Test
    void resetRebuildsAndKeepsForeignChannels() {
        developer.setCardsInfoThreadID("dev-thread");
        developer.setPrivateChannelID("42");
        TestBedChannelService.createFogPrivateChannel(game, developer, null);
        assertTrue(TestBedChannelService.createdChannelIds(game).isEmpty());

        TestBedService.markAsTestBed(game, true);
        TestBedChannelService.recordCreatedChannel(game, "seat-thread");
        game.setTile(new Tile("19", "101"));
        game.drawActionCard(nekro.getUserID(), 3);
        game.setSCPlayed(3, true);
        TestBedComponentService.applyGameState(
                game,
                TestBedPresetService.parse("{ \"revealedObjectives\": [\"corner\"], \"laws\": [\"arms_reduction\"] }"),
                new ArrayList<>());
        int fullDeck = Mapper.getDeck(game.getAcDeckID()).getNewShuffledDeck().size();

        assertEquals(new ResetResult(1, 1, 1, false), TestBedResetService.reset(game));
        assertNull(game.getPlayer(nekro.getUserID()));
        assertFalse(game.getPlayer(DEV_ID).isRealPlayer());
        assertEquals("dev-thread", game.getPlayer(DEV_ID).getCardsInfoThreadID());
        assertEquals(fullDeck, game.getActionCards().size());
        assertTrue(game.getPlayedSCs().isEmpty());
        assertTrue(game.getTileMap().isEmpty());
        // Preset laws and objectives must not survive the rebuild once the test bed marker is gone
        assertTrue(game.getLaws().isEmpty());
        assertFalse(game.getRevealedPublicObjectives().containsKey("corner"));
        assertTrue(game.getAgendas().contains("arms_reduction"));
        assertFalse(TestBedService.isMarkedAsTestBed(game));
    }

    // Fog options and stored values from a preset are what the map code reads: options switch on, and segment
    // definitions (which contain the `:` the script `setStored` guard refuses) arrive intact.
    @Test
    void presetFogOptionsAndStoredValuesReachTheMapCode() {
        game.setFowMode(true);
        TestBedPreset preset = TestBedPresetService.parse("""
                { "fowOptions": ["map_connections", "GHOST_HEXES"],
                  "stored": { "fowMapSegments": "core=000:3;outpost=a000:1" } }""");

        TestBedComponentService.applyGameState(game, preset, new ArrayList<>());

        assertTrue(game.getFowOption(FOWOption.MAP_CONNECTIONS));
        assertTrue(game.getFowOption(FOWOption.GHOST_HEXES));
        assertEquals(
                List.of(new MapSegment("core", "000", 3), new MapSegment("outpost", "a000", 1)),
                MapSegment.stored(game));
    }

    // A timed-out step says what the game is still waiting on, per seat.
    @Test
    void waitReasonsNameWhatIsPending() {
        game.setPhaseOfGame("statusScoring");
        game.updateActivePlayer(nekro);
        game.setStoredValue("solround" + game.getRound() + "PO", "done");

        List<String> nekroWaits = TestBedWaitReasons.forSeat(game, nekro);
        assertTrue(
                nekroWaits.containsAll(List.of("its turn", "public scoring", "secret scoring")), nekroWaits.toString());
        assertEquals(List.of("secret scoring"), TestBedWaitReasons.forSeat(game, developer));
        assertTrue(TestBedWaitReasons.describe(game).startsWith("phase statusScoring"));
    }

    // Preset components land where the game keeps them; every state path scripts can read exists; placeholders
    // turn card ids into hand numbers; a requested card is found wherever it is.
    @Test
    void componentsStateAndPlaceholders() {
        developer.initPNs();
        nekro.initPNs();
        game.setTile(new Tile("19", "101"));
        TestBedPreset preset = TestBedPresetService.parse("""
                { "you": { "pns": ["sftt:sol"], "scoredObjectives": ["corner"], "fragments": ["crf1"] },
                  "revealedObjectives": ["corner"], "laws": ["arms_reduction"], "tokens": { "101": ["frontier"] } }""");
        List<String> warnings = new ArrayList<>();
        TestBedComponentService.applyGameState(game, preset, warnings);
        TestBedComponentService.applySeatComponents(game, nekro, preset.getYou(), warnings);
        assertEquals(List.of(), warnings);
        assertTrue(game.getLaws().containsKey("arms_reduction"));
        assertTrue(game.getScoredPublicObjectives().get("corner").contains(nekro.getUserID()));
        assertTrue(nekro.getPromissoryNotes().containsKey("red_sftt"));
        assertTrue(nekro.getFragments().contains("crf1"));

        for (String field : TestBedStateResolver.SEAT_FIELDS) {
            assertFalse(resolve("nekro." + field).startsWith("<"), field);
        }
        for (String field : TestBedStateResolver.GAME_FIELDS) {
            assertFalse(resolve("game." + field).startsWith("<"), field);
        }
        assertEquals("corner", resolve("nekro.posScored"));

        // Tile and planet paths read the board itself.
        Tile wellon = game.getTileByPosition("101");
        wellon.getSpaceUnitHolder().addUnit(Units.getUnitKey(UnitType.Destroyer, "blue"), 2);
        wellon.getSpaceUnitHolder().addCC("blue");
        nekro.addPlanet("wellon");
        assertEquals("space:blue_dd=2", resolve("tile.101.units"));
        assertTrue(resolve("tile.101.ccs").contains("blue"), resolve("tile.101.ccs"));
        assertEquals("wellon", resolve("tile.101.planets"));
        assertEquals("nekro", resolve("planet.wellon.owner"));
        assertEquals("<no tile at 999>", resolve("tile.999.units"));
        assertEquals(null, TestBedStateResolver.validatePath("tile.101.units"));
        assertEquals(null, TestBedStateResolver.validatePath("planet.wellon.owner"));
        assertTrue(TestBedStateResolver.validatePath("tile.101.mood").contains("unknown tile."));
        assertTrue(TestBedStateResolver.validatePath("tile.units").contains("must be"));

        game.drawSpecificActionCard("sabo1", nekro.getUserID());
        String resolved = TestBedPlaceholders.resolve(
                        "ac_{ac:sabo1}_{nekro.color}", nekro, game::getPlayerFromColorOrFaction)
                .text();
        assertEquals("ac_" + nekro.getActionCards().get("sabo1") + "_blue", resolved);

        // Asking for a card another seat already holds moves it to you instead of failing.
        TestBedPreset wantsSabotage = TestBedPresetService.parse("{ \"you\": { \"acs\": [\"sabo1\"] } }");
        TestBedApplyService.applyHand(game, developer, wantsSabotage.getYou(), null, warnings);
        assertEquals(List.of(), warnings);
        assertTrue(developer.getActionCards().containsKey("sabo1"));
        assertFalse(nekro.getActionCards().containsKey("sabo1"));
    }

    private String resolve(String path) {
        return TestBedStateResolver.resolve(game, path, game::getPlayerFromColorOrFaction);
    }

    // At 8 seats the main panel uses 3 rows; the turn buttons page and every test buttons page fits Discord's limits
    // however many buttons
    // there are; button ids parse back; autocomplete labels stay within 100 characters.
    @Test
    void panelFitsDiscord() {
        IntStream.range(2, 8).forEach(i -> TestBedFixture.virtualSeat(game, i, FACTIONS.get(i), COLORS.get(i)));
        List<ActionRow> main = TestBedPanelService.components(game, nekro, false);
        assertTrue(main.size() <= 3, "main rows: " + main.size());
        assertWithinLimits(main);

        // A turn message can carry Discord's maximum of 25 buttons; the page keeps 20 plus its own row.
        List<Button> turnButtons = IntStream.range(0, 25)
                .mapToObj(i -> Buttons.green("FFCC_nekro_option" + i, "Option " + i))
                .toList();
        TurnButtons turn = new TurnButtons(nekro, null, turnButtons);
        assertWithinLimits(TestBedPanelService.turnComponents(game, turn));
        assertTrue(TestBedPanelService.turnContent(game, turn, null).contains("nekro"));

        // In a combat thread the page also offers a "Roll as" button per side, so it keeps 15 buttons instead.
        Player other = game.getRealPlayers().stream()
                .filter(seat -> seat != nekro)
                .findFirst()
                .orElseThrow();
        MessageChannelUnion thread = mock(MessageChannelUnion.class);
        when(thread.getName()).thenReturn(game.getName() + "-round-1-system-101-turn-1-nekro-vs-" + other.getFaction());
        when(thread.getAsMention()).thenReturn("<#1>");
        Message combatMessage = mock(Message.class);
        when(combatMessage.getChannel()).thenReturn(thread);
        TurnButtons combat = new TurnButtons(nekro, combatMessage, turnButtons, other, true);
        List<ActionRow> combatRows = TestBedPanelService.turnComponents(game, combat);
        assertWithinLimits(combatRows);
        assertEquals(15, combat.shown().size());
        List<String> rollAs = combatRows.get(combatRows.size() - 2).getButtons().stream()
                .map(Button::getLabel)
                .toList();
        assertEquals(2, rollAs.size());
        assertTrue(rollAs.containsAll(List.of("Roll as nekro", "Roll as " + other.getFaction())), rollAs.toString());

        Shortcut shortcut = new Shortcut();
        shortcut.setLabel("A shortcut label that is rather long to see the truncation stay within limits ok");
        TestBedShortcuts.store(
                game, IntStream.range(0, 30).mapToObj(i -> shortcut).toList());
        List<ButtonGroup> groups = TestBedShortcuts.groups(game);
        assertTrue(groups.stream().map(ButtonGroup::key).toList().containsAll(List.of("seat", "game", "preset")));
        for (ButtonGroup group : groups) {
            for (int page = 0; page < TestBedPanelService.pageCount(group); page++) {
                assertWithinLimits(TestBedPanelService.pageComponents(groups, group, page));
            }
        }
        assertEquals(
                new PageRef("file-fog-qol", 17),
                TestBedPanelService.parseRef(TestBedPanelService.RUN + "file-fog-qol_17", TestBedPanelService.RUN));
        for (String option : List.of(TestBedAutoComplete.PRESET_OPTION, TestBedAutoComplete.SCRIPT_OPTION)) {
            TestBedAutoComplete.choices(option, game, "")
                    .forEach(choice -> assertTrue(choice.getName().length() <= 100, choice.getName()));
        }
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
            row.getComponents().stream()
                    .filter(StringSelectMenu.class::isInstance)
                    .map(StringSelectMenu.class::cast)
                    .forEach(menu -> assertTrue(menu.getOptions().size() <= 25));
        }
    }
}
