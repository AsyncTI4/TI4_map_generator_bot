package ti4.service.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class WebhookGameEventNotifierTest extends BaseTi4Test {

    @Test
    void notifyPhaseChanged_MapsStatusPhase() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
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
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
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
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
        Game game = createConfiguredGame();
        GameWebhookConfig.clearWebhookConfig(game);

        notifier.notifyPhaseChanged(game, "strategy", "action");

        assertTrue(dispatchService.calls.isEmpty());
    }

    @Test
    void notifyPhaseChanged_DoesNotDispatch_WhenDisabled() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
        Game game = createConfiguredGame();
        GameWebhookConfig.setWebhookEnabled(game, false);

        notifier.notifyPhaseChanged(game, "strategy", "action");

        assertTrue(dispatchService.calls.isEmpty());
    }

    @Test
    void notifyPhaseChanged_DoesNotDispatch_ForFowUnlessExplicitlyAllowed() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
        Game game = createConfiguredGame();
        game.setFowMode(true);

        notifier.notifyPhaseChanged(game, "strategy", "action");
        assertTrue(dispatchService.calls.isEmpty());

        GameWebhookConfig.setFowAllowed(game, true);
        notifier.notifyPhaseChanged(game, "strategy", "action");
        assertEquals(1, dispatchService.calls.size());
    }

    @Test
    void notifyPhaseChanged_DoesNotPropagateDispatchFailures() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        dispatchService.throwOnDispatch = true;
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
        Game game = createConfiguredGame();

        assertDoesNotThrow(() -> notifier.notifyPhaseChanged(game, "strategy", "action"));
    }

    @Test
    void notifyPhaseChanged_SkipsUnknownPhaseTransitions() {
        CapturingDispatchService dispatchService = new CapturingDispatchService();
        WebhookGameEventNotifier notifier = new WebhookGameEventNotifier(dispatchService);
        Game game = createConfiguredGame();

        notifier.notifyPhaseChanged(game, "miltydraft", "playerSetup");

        assertTrue(dispatchService.calls.isEmpty());
    }

    private static Game createConfiguredGame() {
        Game game = new Game();
        game.setName("pbd-test");
        game.setRound(2);
        Player player = game.addPlayer("p1", "Player 1");
        player.setFaction("hacan");
        player.setColor("yellow");
        GameWebhookConfig.setWebhookUrl(game, "https://example.com/hook");
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
