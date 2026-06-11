package ti4.service.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.spring.service.webhook.WebhookUserEntity;

class GameWebhookNotifierTest {

    private final GameWebhookSubscriptionService subscriptionService = mock(GameWebhookSubscriptionService.class);
    private final WebhookDispatchService dispatchService = mock(WebhookDispatchService.class);
    private final GameWebhookNotifier notifier = new GameWebhookNotifier(subscriptionService, dispatchService);

    @Test
    void turnChangedDispatchesToActiveSubscribers() {
        Game game = mock(Game.class);
        Player activePlayer = mock(Player.class);
        WebhookUserEntity subscriber = subscriber("https://example.test/webhook", true);
        when(game.getName()).thenReturn("pbd1234");
        when(game.getPhaseOfGame()).thenReturn("action");
        when(game.getRound()).thenReturn(3);
        when(game.getActivePlayer()).thenReturn(activePlayer);
        when(activePlayer.getUserID()).thenReturn("123456789");
        when(activePlayer.getFaction()).thenReturn("arborec");
        when(subscriptionService.getSubscribers("pbd1234", GameWebhookEventType.TURN_CHANGED))
                .thenReturn(List.of(subscriber));

        notifier.turnChanged(game);

        ArgumentCaptor<GameWebhookPayload> payload = ArgumentCaptor.forClass(GameWebhookPayload.class);
        verify(dispatchService).dispatch("https://example.test/webhook", payload.capture());
        assertEquals("pbd1234", payload.getValue().gameName());
        assertEquals(GameWebhookEventType.TURN_CHANGED, payload.getValue().eventType());
        assertEquals("action", payload.getValue().phaseOfGame());
        assertEquals(3, payload.getValue().round());
        assertEquals("123456789", payload.getValue().activePlayerId());
        assertEquals("arborec", payload.getValue().activeFaction());
        assertNotNull(payload.getValue().timestamp());
    }

    @Test
    void phaseChangedIgnoresSameNormalizedPhase() {
        Game game = mock(Game.class);

        boolean emitted = notifier.phaseChanged(game, "statusHomework", "statusScoring");

        assertFalse(emitted);
        verifyNoInteractions(subscriptionService, dispatchService);
    }

    @Test
    void turnChangedIgnoresUnknownPhase() {
        Game game = mock(Game.class);
        when(game.getPhaseOfGame()).thenReturn("miltydraft");

        notifier.turnChanged(game);

        verifyNoInteractions(subscriptionService, dispatchService);
    }

    @Test
    void phaseChangedDispatchesPreviousAndCurrentPhase() {
        Game game = mock(Game.class);
        WebhookUserEntity subscriber = subscriber("https://example.test/phase", true);
        when(game.getName()).thenReturn("pbd1234");
        when(game.getRound()).thenReturn(2);
        when(subscriptionService.getSubscribers("pbd1234", GameWebhookEventType.PHASE_CHANGED))
                .thenReturn(List.of(subscriber));

        boolean emitted = notifier.phaseChanged(game, "strategy", "action");

        assertTrue(emitted);
        ArgumentCaptor<GameWebhookPayload> payload = ArgumentCaptor.forClass(GameWebhookPayload.class);
        verify(dispatchService).dispatch("https://example.test/phase", payload.capture());
        assertEquals(GameWebhookEventType.PHASE_CHANGED, payload.getValue().eventType());
        assertEquals("strategy", payload.getValue().previousPhaseOfGame());
        assertEquals("action", payload.getValue().phaseOfGame());
        assertEquals(2, payload.getValue().round());
    }

    private static WebhookUserEntity subscriber(String callbackUrl, boolean active) {
        WebhookUserEntity subscriber = new WebhookUserEntity();
        subscriber.setCallbackUrl(callbackUrl);
        subscriber.setActive(active);
        return subscriber;
    }
}
