package ti4.service.fow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.option.FOWOptionService.FOWOption;

class AnonymousCommsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    // No stored ping means the receiver is armed and the next message pings.
    @Test
    void firstMessagePings() {
        assertTrue(AnonymousCommsService.shouldPing(null, NOW));
    }

    @Test
    void recentPingSilencesFollowUps() {
        long oneHourAgo = NOW.minus(Duration.ofHours(1)).toEpochMilli();
        assertFalse(AnonymousCommsService.shouldPing(oneHourAgo, NOW));
    }

    // A player who only reads never acknowledges, so the timer re-arms the ping.
    @Test
    void pingRearmsAfterTimer() {
        long atLimit =
                NOW.minus(Duration.ofHours(AnonymousCommsService.REPING_HOURS)).toEpochMilli();
        assertTrue(AnonymousCommsService.shouldPing(atLimit, NOW));
    }

    @Test
    void markerFoundWithOrWithoutPing() {
        Map<String, String> markers = Map.of("📨 RED:", "red", "📨 BLUE:", "blue");
        assertEquals("red", AnonymousCommsService.earliestMarkerColor("📨 RED: hello", markers));
        assertEquals("blue", AnonymousCommsService.earliestMarkerColor("<@123> 📨 BLUE: hi", markers));
    }

    // The preview is player text, so it can quote another color's marker after the real one.
    @Test
    void earliestMarkerWinsOverQuotedMarker() {
        Map<String, String> markers = Map.of("📨 RED:", "red", "📨 BLUE:", "blue");
        assertEquals("blue", AnonymousCommsService.earliestMarkerColor("📨 BLUE: look 📨 RED: fake", markers));
    }

    @Test
    void noMarkerGivesNoTarget() {
        assertNull(AnonymousCommsService.earliestMarkerColor("📇 Comms", Map.of("📨 RED:", "red")));
    }

    @Test
    void hubColorsPartnersGreenOthersRed() {
        Map<String, Boolean> styles = AnonymousCommsService.hubButtonStyles(
                Set.of("red", "blue"), List.of("blue", "green"), Set.of("red", "blue", "green", "pink"), "pink");
        assertEquals(Map.of("red", false, "blue", true, "green", true), styles);
    }

    // Own color and colors no longer in the game never get a button.
    @Test
    void hubSkipsSelfAndGoneColors() {
        Map<String, Boolean> styles = AnonymousCommsService.hubButtonStyles(
                Set.of("pink", "gone", "red"), List.of(), Set.of("red", "pink"), "pink");
        assertEquals(Map.of("red", false), styles);
    }

    // Guild Ships lets Hacan reach a color it has met, but the other side must not learn that from its own comms:
    // it can only answer once Hacan has written to it.
    @Test
    void guildShipsIsOneWayUntilHacanWrites() {
        Map<String, String> stored = new HashMap<>();
        Player hacan = player("green", true);
        Player red = player("red", false);
        Game game = anonCommsGame(stored, List.of(hacan, red));
        stored.put("fowCommsEncountered_green", "red");

        assertTrue(AnonymousCommsService.canTalk(game, hacan, red));
        assertFalse(AnonymousCommsService.canTalk(game, red, hacan));

        AnonymousCommsService.grantReplyIfOneWay(game, hacan, red);
        assertTrue(AnonymousCommsService.canTalk(game, red, hacan));
    }

    @Test
    void guildShipsNeedsAnEncounter() {
        Player hacan = player("green", true);
        Player red = player("red", false);
        Game game = anonCommsGame(new HashMap<>(), List.of(hacan, red));

        assertFalse(AnonymousCommsService.canTalk(game, hacan, red));
    }

    private static Player player(String color, boolean guildShips) {
        Player player = mock(Player.class);
        when(player.getColor()).thenReturn(color);
        when(player.isRealPlayer()).thenReturn(true);
        when(player.hasAbility("guild_ships")).thenReturn(guildShips);
        when(player.getNeighbouringPlayers(true)).thenReturn(Set.of());
        return player;
    }

    private static Game anonCommsGame(Map<String, String> stored, List<Player> players) {
        Game game = mock(Game.class);
        when(game.isFowMode()).thenReturn(true);
        when(game.getFowOption(FOWOption.ANONYMOUS_COMMS)).thenReturn(true);
        when(game.getPhaseOfGame()).thenReturn("action");
        when(game.getRealPlayers()).thenReturn(players);
        when(game.getStoredValueMap()).thenReturn(stored);
        when(game.getStoredValue(anyString()))
                .thenAnswer(call -> stored.getOrDefault(call.getArgument(0, String.class), ""));
        doAnswer(call -> stored.put(call.getArgument(0), call.getArgument(1)))
                .when(game)
                .setStoredValue(anyString(), anyString());
        return game;
    }
}
