package ti4.spring.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ti4.map.Game;
import ti4.map.Player;
import ti4.spring.service.ActionCardDeckService;
import ti4.spring.service.auth.GameSecurityService;
import ti4.spring.service.auth.RequestContextTestUtil;
import ti4.spring.service.auth.SecurityContextTestUtil;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActionCardDeckController.class)
@AutoConfigureMockMvc
class ActionCardDeckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "security")
    private GameSecurityService security;

    @MockitoBean
    private ActionCardDeckService deckService;

    private Game game;
    private Player player;

    @BeforeEach
    void beforeEach() {
        var game = new Game();
        game.setName("test");

        var player = new Player("player1", "tester", game);
        game.setPlayer("player1", player);

        RequestContextTestUtil.setGame(game);
        SecurityContextTestUtil.setupSecurityContext("player1");
    }

    @AfterEach
    void afterEach() {
        RequestContextTestUtil.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shuffleCallsServiceAndReturnsOk() throws Exception {
        when(security.canAccessGame("test")).thenReturn(true);

        mockMvc.perform(post("/api/game/test/action-card-deck/shuffle"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Shuffled")));

        verify(deckService).shuffle(game, player);
    }
}
