package ti4.spring.controller;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import ti4.map.Game;
import ti4.map.Player;
import ti4.spring.service.ActionCardDeckService;
import ti4.spring.service.auth.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class ActionCardDeckControllerTest {
    private ActionCardDeckService deckService;
    private ActionCardDeckController controller;
    private Game game;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
        deckService = Mockito.mock(ActionCardDeckService.class);
        controller = new ActionCardDeckController(deckService);
        game = new Game();
        game.setName("game1");
        player = new Player("uid", "user", game);
        game.setPlayers(Map.of("uid", player));

        setRequestContextGame(game);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("uid", null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void cleanup() throws Exception {
        clearRequestContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shuffleCallsServiceAndReturnsOk() {
        ResponseEntity<String> response = controller.shuffle("game1");
        verify(deckService).shuffle(game, player);
        assertThat(response.getBody()).contains("Shuffled");
    }

    private static void setRequestContextGame(Game game) throws Exception {
        Method m = RequestContext.class.getDeclaredMethod("setGame", Game.class);
        m.setAccessible(true);
        m.invoke(null, game);
    }

    private static void clearRequestContext() throws Exception {
        Method m = RequestContext.class.getDeclaredMethod("clearContext");
        m.setAccessible(true);
        m.invoke(null);
    }
}
