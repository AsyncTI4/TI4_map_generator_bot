package ti4.service.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.testUtils.BaseTi4Test;

class WebhookGameEventNotifierTest extends BaseTi4Test {

    @Test
    void notifyPhaseChanged_MapsStatusPhase() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = newNotifier(dispatchService);
        Game game = createConfiguredGame();
        game.setPhaseOfGame("statusHomework");

        notifier.notifyPhaseChanged(game, "action", "statusHomework");

        assertEquals(1, dispatchService.calls.size());
        GameWebhookEventPayload payload = dispatchService.calls.getFirst().payload;
        assertEquals("phase_changed", payload.eventType());
        assertEquals("status", payload.phaseOfGame());
        assertEquals("action", payload.metadata().get("previousPhaseOfGame"));
        assertEquals("status", payload.metadata().get("currentPhaseOfGame"));
    }

    @Test
    void notifyPlayerPassed_BuildsExpectedPayload() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = newNotifier(dispatchService);
        Game game = createConfiguredGame();
        Player activePlayer = game.getPlayer("p1");
        game.updateActivePlayer(activePlayer);

        notifier.notifyPlayerPassed(game, activePlayer, false);

        assertEquals(1, dispatchService.calls.size());
        GameWebhookEventPayload payload = dispatchService.calls.getFirst().payload;
        assertEquals(game.getName(), payload.gameName());
        assertEquals("player_passed", payload.eventType());
        assertEquals(game.getRound(), payload.round());
        assertEquals(activePlayer.getUserID(), payload.activePlayerId());
        assertEquals(activePlayer.getFaction(), payload.activeFaction());
        assertEquals(activePlayer.getUserID(), payload.metadata().get("passedPlayerId"));
        assertEquals(activePlayer.getFaction(), payload.metadata().get("passedFaction"));
        assertEquals(false, payload.metadata().get("autoPass"));
    }

    @Test
    void notifyPhaseChanged_DoesNotDispatch_WhenNoWebhookUrl() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = newNotifier(dispatchService);
        Game game = createConfiguredGame();

        notifier.notifyPhaseChanged(game, "strategy", "action");

        assertTrue(dispatchService.calls.isEmpty());
    }

    @Test
    void notifyPhaseChanged_DoesNotPropagateDispatchFailures() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        dispatchService.throwOnDispatch = true;
        WebhookGameEventNotifier notifier = newNotifier(dispatchService);
        Game game = createConfiguredGame();

        assertDoesNotThrow(() -> notifier.notifyPhaseChanged(game, "strategy", "action"));
    }

    @Test
    void notifyPhaseChanged_SkipsUnknownPhaseTransitions() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = newNotifier(dispatchService);
        Game game = createConfiguredGame();

        notifier.notifyPhaseChanged(game, "miltydraft", "playerSetup");

        assertTrue(dispatchService.calls.isEmpty());
    }

    @Test
    void notifyPhaseChanged_DispatchesToSubscriptions() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        GameWebhookSubscriptionService subscriptionService = mock(GameWebhookSubscriptionService.class);
        when(subscriptionService.getCallbackUrls(eq("pbd-test"), eq(GameWebhookEventType.PHASE_CHANGED)))
                .thenReturn(List.of("https://example.com/callback"));
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService, subscriptionService);

        Game game = createConfiguredGame();

        notifier.notifyPhaseChanged(game, "strategy", "action");

        assertEquals(1, dispatchService.calls.size());
        assertEquals("https://example.com/callback", dispatchService.calls.getFirst().webhookUrl);
    }

    private static WebhookGameEventNotifier newNotifier(CapturingDispatchService dispatchService) {
        GameWebhookSubscriptionService subscriptionService = mock(GameWebhookSubscriptionService.class);
        when(subscriptionService.getCallbackUrls(any(), any())).thenReturn(List.of());
        return new WebhookGameEventNotifier(dispatchService, subscriptionService);
    }

    private static Game createConfiguredGame() {
        Game game = new Game();
        game.setName("pbd-test");
        game.setRound(2);
        Player player = game.addPlayer("p1", "Player 1");
        player.setFaction("hacan");
        player.setColor("yellow");
        return game;
    }

    static class CapturingDispatchService extends WebhookDispatchService {
        List<DispatchCall> calls = new ArrayList<>();
        boolean throwOnDispatch;

        @Override
        public void dispatch(Game game, String webhookUrl, GameWebhookEventPayload payload) {
            if (throwOnDispatch) {
                throw new RuntimeException("Dispatch failed");
            }
            calls.add(new DispatchCall(game, webhookUrl, payload));
        }
    }

    record DispatchCall(Game game, String webhookUrl, GameWebhookEventPayload payload) {}
}
