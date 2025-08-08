package ti4.spring.service.auth;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.HandlerMapping;

import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.spring.exception.InvalidGameNameException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class GameLockAndRequestContextInterceptorTest {
    private Game game;
    private Player player;
    private ConcurrentMap<String, ManagedGame> gameMap;
    private GameLockAndRequestContextInterceptor interceptor;

    @BeforeEach
    void setup() throws Exception {
        interceptor = new GameLockAndRequestContextInterceptor();
        game = new Game();
        game.setName("test");
        player = new Player("uid", "name", game);
        game.setPlayers(Map.of("uid", player));

        Field f = GameManager.class.getDeclaredField("gameNameToManagedGame");
        f.setAccessible(true);
        gameMap = (ConcurrentMap<String, ManagedGame>) f.get(null);
        gameMap.put("test", new ManagedGame(game));
    }

    @AfterEach
    void cleanup() {
        gameMap.clear();
        RequestContext.clearContext();
    }

    @Test
    void preHandleThrowsForInvalidGame() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        when(req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(Map.of("gameName", "bad"));
        assertThatThrownBy(() -> interceptor.preHandle(req, res, new Object()))
            .isInstanceOf(InvalidGameNameException.class);
    }

    @Test
    void preHandleSetsContextAndAfterCompletionClears() throws Exception {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        when(req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(Map.of("gameName", "test"));
        when(req.getMethod()).thenReturn("GET");

        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
        assertThat(RequestContext.getGame()).isSameAs(game);

        interceptor.afterCompletion(req, res, new Object(), null);
        assertThat(RequestContext.getGame()).isNull();
    }
}
