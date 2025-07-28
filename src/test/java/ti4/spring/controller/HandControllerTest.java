package ti4.spring.controller;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import ti4.map.Game;
import ti4.map.Player;
import ti4.spring.model.GetHandResponse;
import ti4.spring.service.HandService;
import ti4.spring.service.auth.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = HandController.class)
class HandControllerTest {

    @MockBean
    private HandService handService;

    @Autowired
    private HandController controller;
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
    void getReturnsPlayerHand() {
        GetHandResponse response = controller.get("testGame");
        assertThat(response.actionCards()).containsExactly("ac");
        assertThat(response.secretObjectives()).containsExactly("so");
        assertThat(response.promissoryNotes()).containsExactly("pn");
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
