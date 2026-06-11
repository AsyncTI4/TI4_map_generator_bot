package ti4.spring.api.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.service.webhook.GameWebhookEventType;
import ti4.spring.context.SetupRequestContext;
import ti4.spring.service.webhook.GameWebhookEntity;
import ti4.spring.service.webhook.GameWebhookRepository;
import ti4.spring.service.webhook.GameWebhookSubscriptionService;
import ti4.spring.service.webhook.GameWebhookSubscriptionService.SubscribeResult;
import ti4.spring.service.webhook.WebhookUserEntity;
import ti4.spring.service.webhook.WebhookUserRepository;

class GameWebhookApiControllerTest {

    @Test
    void eventTypesReturnsCompactMetadata() {
        GameWebhookApiController controller = new GameWebhookApiController(mock(GameWebhookSubscriptionService.class));

        var response = controller.getEventTypes();

        assertEquals(2, response.size());
        assertEquals("TURN_CHANGED", response.getFirst().name());
        assertEquals("A new player's turn has started.", response.getFirst().description());
        assertEquals(
                "Emitted after the bot updates the active player in StartTurnService.turnStart.",
                response.getFirst().trigger());
        assertEquals("POST", response.getFirst().method());
        assertEquals("application/json", response.getFirst().contentType());
        assertEquals(5, response.getFirst().timeoutSeconds());
        assertEquals(
                "Transient failures only: timeout, IO error, HTTP 408, 429, or 5xx",
                response.getFirst().retries());
        assertEquals("string", response.getFirst().payloadFields().get("gameName"));
        assertEquals("PHASE_CHANGED", response.get(1).name());
        assertEquals("string|null", response.get(1).payloadFields().get("previousPhaseOfGame"));
    }

    @Test
    void mutatingEndpointsUseGameIdPathVariableAndSkipRequestContext() throws NoSuchMethodException {
        Method put = GameWebhookApiController.class.getMethod(
                "put", String.class, String.class, GameWebhookSubscriptionRequest.class);
        Method delete = GameWebhookApiController.class.getMethod("delete", String.class, String.class);

        assertEquals("/{gameId}/webhook", put.getAnnotation(PutMapping.class).value()[0]);
        assertEquals("/{gameId}/webhook", delete.getAnnotation(DeleteMapping.class).value()[0]);
        assertEquals(false, put.getAnnotation(SetupRequestContext.class).value());
        assertEquals(false, delete.getAnnotation(SetupRequestContext.class).value());
    }

    @Test
    void putRejectsMissingApiKey() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd1234", null, new GameWebhookSubscriptionRequest(List.of("TURN_CHANGED")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(service, never()).subscribe(any(), any(), any());
    }

    @Test
    void putMapsUnknownGameToNotFound() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.subscribe("pbd9999", "key", List.of("TURN_CHANGED"))).thenReturn(SubscribeResult.UNKNOWN_GAME);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd9999", "key", new GameWebhookSubscriptionRequest(List.of("TURN_CHANGED")));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void putMapsServiceUnauthorizedToUnauthorized() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.subscribe("pbd1234", "key", List.of("TURN_CHANGED"))).thenReturn(SubscribeResult.UNAUTHORIZED);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd1234", "key", new GameWebhookSubscriptionRequest(List.of("TURN_CHANGED")));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void putMapsUnsupportedEventTypeToBadRequest() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.subscribe("pbd1234", "key", List.of("AGENDA_CHANGED")))
                .thenReturn(SubscribeResult.UNSUPPORTED_EVENT_TYPE);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd1234", "key", new GameWebhookSubscriptionRequest(List.of("AGENDA_CHANGED")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void putMapsForbiddenGameToForbidden() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.subscribe("pbd1234", "key", List.of("TURN_CHANGED"))).thenReturn(SubscribeResult.FORBIDDEN_GAME);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd1234", "key", new GameWebhookSubscriptionRequest(List.of("TURN_CHANGED")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void putMapsSuccessfulSubscriptionToOk() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.subscribe("pbd1234", "key", List.of("TURN_CHANGED"))).thenReturn(SubscribeResult.OK);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.put("pbd1234", "key", new GameWebhookSubscriptionRequest(List.of("TURN_CHANGED")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteRejectsMissingApiKey() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.delete("pbd1234", "");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(service, never()).delete(any(), any());
    }

    @Test
    void deleteMapsSuccessfulDeletionToOk() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.delete("pbd1234", "key")).thenReturn(SubscribeResult.OK);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.delete("pbd1234", "key");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteMapsUnknownGameToNotFound() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.delete("pbd9999", "key")).thenReturn(SubscribeResult.UNKNOWN_GAME);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.delete("pbd9999", "key");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteMapsForbiddenGameToForbidden() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.delete("pbd1234", "key")).thenReturn(SubscribeResult.FORBIDDEN_GAME);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.delete("pbd1234", "key");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteMapsServiceUnauthorizedToUnauthorized() {
        GameWebhookSubscriptionService service = mock(GameWebhookSubscriptionService.class);
        when(service.delete("pbd1234", "key")).thenReturn(SubscribeResult.UNAUTHORIZED);
        GameWebhookApiController controller = new GameWebhookApiController(service);

        var response = controller.delete("pbd1234", "key");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void subscribeHashesApiKeyAndSavesSortedDistinctUppercaseEventTypes() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        when(webhookRepository.findByGameNameAndWebhookUserId("pbd1234", 7L)).thenReturn(Optional.empty());
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(publicGame());
            result = service.subscribe("pbd1234", apiKey, List.of("phase_changed", "TURN_CHANGED", "turn_changed"));
        }

        ArgumentCaptor<GameWebhookEntity> captor = ArgumentCaptor.forClass(GameWebhookEntity.class);
        verify(webhookRepository).save(captor.capture());
        assertEquals(SubscribeResult.OK, result);
        assertEquals("pbd1234", captor.getValue().getGameName());
        assertEquals(7L, captor.getValue().getWebhookUserId());
        assertEquals("PHASE_CHANGED,TURN_CHANGED", captor.getValue().getEventTypesCsv());
    }

    @Test
    void subscribeRejectsUnsupportedEventTypeWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(publicGame());
            result = service.subscribe("pbd1234", apiKey, List.of("TURN_CHANGED", "AGENDA_CHANGED"));
        }

        assertEquals(SubscribeResult.UNSUPPORTED_EVENT_TYPE, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void subscribeTreatsNullEventTypeAsUnsupportedWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(publicGame());
            result = service.subscribe("pbd1234", apiKey, Arrays.asList("TURN_CHANGED", null));
        }

        assertEquals(SubscribeResult.UNSUPPORTED_EVENT_TYPE, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void subscribeRejectsInvalidApiKeyWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        String apiKey = "invalid-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.empty());
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result = service.subscribe("pbd1234", apiKey, List.of("TURN_CHANGED"));

        assertEquals(SubscribeResult.UNAUTHORIZED, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void subscribeRejectsUnknownGameWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd9999")).thenReturn(false);
            result = service.subscribe("pbd9999", apiKey, List.of("TURN_CHANGED"));
        }

        assertEquals(SubscribeResult.UNKNOWN_GAME, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void subscribeRejectsFowGameWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(fowGame());
            result = service.subscribe("pbd1234", apiKey, List.of("TURN_CHANGED"));
        }

        assertEquals(SubscribeResult.FORBIDDEN_GAME, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void subscribeRejectsMissingManagedGameWithoutSaving() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(null);
            result = service.subscribe("pbd1234", apiKey, List.of("TURN_CHANGED"));
        }

        assertEquals(SubscribeResult.FORBIDDEN_GAME, result);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void deleteHashesApiKeyAndDeletesSubscription() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(publicGame());
            result = service.delete("pbd1234", apiKey);
        }

        assertEquals(SubscribeResult.OK, result);
        verify(webhookRepository).deleteByGameNameAndWebhookUserId("pbd1234", 7L);
    }

    @Test
    void deleteRejectsInvalidApiKeyWithoutDeleting() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        String apiKey = "invalid-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.empty());
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result = service.delete("pbd1234", apiKey);

        assertEquals(SubscribeResult.UNAUTHORIZED, result);
        verify(webhookRepository, never()).deleteByGameNameAndWebhookUserId(any(), any());
    }

    @Test
    void deleteRejectsUnknownGameWithoutDeleting() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd9999")).thenReturn(false);
            result = service.delete("pbd9999", apiKey);
        }

        assertEquals(SubscribeResult.UNKNOWN_GAME, result);
        verify(webhookRepository, never()).deleteByGameNameAndWebhookUserId(any(), any());
    }

    @Test
    void deleteRejectsFowGameWithoutDeleting() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(fowGame());
            result = service.delete("pbd1234", apiKey);
        }

        assertEquals(SubscribeResult.FORBIDDEN_GAME, result);
        verify(webhookRepository, never()).deleteByGameNameAndWebhookUserId(any(), any());
    }

    @Test
    void deleteRejectsMissingManagedGameWithoutDeleting() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        WebhookUserEntity user = new WebhookUserEntity();
        user.setId(7L);
        String apiKey = "secret-key";
        when(userRepository.findByApiKeyHashAndActiveTrue(GameWebhookSubscriptionService.sha256(apiKey)))
                .thenReturn(Optional.of(user));
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        SubscribeResult result;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.isValid("pbd1234")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(null);
            result = service.delete("pbd1234", apiKey);
        }

        assertEquals(SubscribeResult.FORBIDDEN_GAME, result);
        verify(webhookRepository, never()).deleteByGameNameAndWebhookUserId(any(), any());
    }

    @Test
    void getSubscribersReturnsEmptyForFowGameWithoutReadingSubscriptions() {
        WebhookUserRepository userRepository = mock(WebhookUserRepository.class);
        GameWebhookRepository webhookRepository = mock(GameWebhookRepository.class);
        GameWebhookSubscriptionService service = new GameWebhookSubscriptionService(userRepository, webhookRepository);

        List<WebhookUserEntity> subscribers;
        try (MockedStatic<GameManager> gameManager = Mockito.mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd1234")).thenReturn(fowGame());
            subscribers = service.getSubscribers("pbd1234", GameWebhookEventType.TURN_CHANGED);
        }

        assertTrue(subscribers.isEmpty());
        verify(webhookRepository, never()).findByGameName(any());
    }

    @Test
    void sha256ReturnsLowercaseHexDigest() {
        assertTrue(GameWebhookSubscriptionService.sha256("secret-key").matches("[0-9a-f]{64}"));
    }

    private static ManagedGame publicGame() {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(false);
        return managedGame;
    }

    private static ManagedGame fowGame() {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(true);
        return managedGame;
    }
}
