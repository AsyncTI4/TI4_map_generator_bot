package ti4.spring.service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import ti4.map.Game;
import ti4.map.Player;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {
    private Game game;
    private Player player;

    @BeforeEach
    void setup() {
        game = new Game();
        game.setName("ctxGame");
        player = new Player("user", "name", game);
        game.setPlayers(java.util.Map.of("user", player));
        RequestContext.setGame(game);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("user", null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void cleanup() {
        RequestContext.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSetGame() {
        assertThat(RequestContext.getGame()).isSameAs(game);
    }

    @Test
    void returnsPlayerFromContext() {
        assertThat(RequestContext.getPlayer()).isSameAs(player);
    }
}
