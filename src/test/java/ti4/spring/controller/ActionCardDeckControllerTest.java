package ti4.spring.controller;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import ti4.map.Game;
import ti4.map.Player;
import ti4.spring.service.ActionCardDeckService;
import ti4.spring.service.auth.RequestContext;

import ti4.spring.service.auth.GameSecurityService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = ActionCardDeckController.class)
@AutoConfigureMockMvc
class ActionCardDeckControllerTest {

    @MockBean
    private ActionCardDeckService deckService;

    @Autowired
    private MockMvc mockMvc;

    @MockBean(name = "security")
    private GameSecurityService security;
    private Game game;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
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
    void shuffleCallsServiceAndReturnsOk() throws Exception {
        when(security.canAccessGame("game1")).thenReturn(true);

        mockMvc.perform(post("/api/game/game1/action-card-deck/shuffle"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Shuffled")));

        verify(deckService).shuffle(game, player);
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
