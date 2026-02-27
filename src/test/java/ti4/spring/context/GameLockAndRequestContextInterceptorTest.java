package ti4.spring.context;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import ti4.executors.ExecutionLockManager;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;

class GameLockAndRequestContextInterceptorTest {

    private final GameLockAndRequestContextInterceptor interceptor = new GameLockAndRequestContextInterceptor();

    @AfterEach
    void tearDown() {
        RequestContext.clearContext();
    }

    @Test
    void preHandleAndAfterCompletion_forGameScopedRequest_locksUnlocksAndClearsContext() {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        var game = mock(Game.class);
        var managedGame = mock(ManagedGame.class);

        org.mockito.Mockito.when(request.getMethod()).thenReturn("GET");
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/game/test-game/status");
        org.mockito.Mockito.when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("gameName", "test-game"));
        org.mockito.Mockito.when(game.getName()).thenReturn("test-game");
        org.mockito.Mockito.when(managedGame.getGame()).thenReturn(game);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class);
                MockedStatic<ExecutionLockManager> lockManager = mockStatic(ExecutionLockManager.class)) {
            gameManager.when(() -> GameManager.isValid("test-game")).thenReturn(true);
            gameManager.when(() -> GameManager.getManagedGame("test-game")).thenReturn(managedGame);

            assertTrue(interceptor.preHandle(request, response, new Object()));
            interceptor.afterCompletion(request, response, new Object(), null);

            lockManager.verify(
                    () -> ExecutionLockManager.lock("test-game", ExecutionLockManager.LockType.READ));
            lockManager.verify(
                    () -> ExecutionLockManager.unlock("test-game", ExecutionLockManager.LockType.READ));
            gameManager.verify(() -> GameManager.save(game, "null called /api/game/test-game/status"), never());
            assertNull(RequestContext.getGame());
        }
    }

    @Test
    void preHandle_withSetupRequestContextFalse_doesNotSetContextOrLockGame() throws NoSuchMethodException {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        var handler = new HandlerMethod(
                new TestController(), TestController.class.getMethod("publicGameEndpoint"));

        org.mockito.Mockito.when(request.getMethod()).thenReturn("POST");
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/public/game/test-game/ping");
        org.mockito.Mockito.when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("gameName", "test-game"));

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class);
                MockedStatic<ExecutionLockManager> lockManager = mockStatic(ExecutionLockManager.class)) {
            gameManager.when(() -> GameManager.isValid("test-game")).thenReturn(true);

            assertTrue(interceptor.preHandle(request, response, handler));
            assertNull(RequestContext.getGame());

            interceptor.afterCompletion(request, response, handler, null);

            gameManager.verify(() -> GameManager.isValid("test-game"));
            lockManager.verifyNoInteractions();
            gameManager.verifyNoMoreInteractions();
            assertNull(RequestContext.getGame());
        }
    }

    @Test
    void afterCompletion_clearsContext_whenGameIsNull() {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);

        try (MockedStatic<RequestContext> requestContext = mockStatic(RequestContext.class)) {
            requestContext.when(RequestContext::getGame).thenReturn(null);

            interceptor.afterCompletion(request, response, new Object(), null);

            requestContext.verify(RequestContext::clearContext);
        }
    }

    private static class TestController {
        @SetupRequestContext(false)
        public void publicGameEndpoint() {}
    }
}
