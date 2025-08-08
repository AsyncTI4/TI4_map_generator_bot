package ti4.spring.controller;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ti4.map.Game;
import ti4.map.Player;
import ti4.spring.service.HandService;
import ti4.spring.service.auth.GameSecurityService;
import ti4.spring.service.auth.RequestContext;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HandController.class)
@AutoConfigureMockMvc
class HandControllerTest {

    @MockitoBean
    private HandService handService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "security")
    private GameSecurityService security;
    private Game game;
    private Player player;

    @BeforeEach
    void setup() throws Exception {
        game = new Game();
        game.setName("testGame");
        player = new Player("uid", "user", game);
        game.setPlayers(Map.of("uid", player));

        setRequestContextGame(game);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("uid", null));
        SecurityContextHolder.setContext(context);

        when(handService.getActionCards(player)).thenReturn(Set.of("ac"));
        when(handService.getSecretObjectives(player)).thenReturn(Set.of("so"));
        when(handService.getPromissoryNotes(player)).thenReturn(Set.of("pn"));
    }

    @AfterEach
    void cleanup() throws Exception {
        clearRequestContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getReturnsPlayerHand() throws Exception {
        when(security.canAccessGame("testGame")).thenReturn(true);

        mockMvc.perform(get("/api/game/testGame/hand"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionCards[0]").value("ac"))
            .andExpect(jsonPath("$.secretObjectives[0]").value("so"))
            .andExpect(jsonPath("$.promissoryNotes[0]").value("pn"));
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
