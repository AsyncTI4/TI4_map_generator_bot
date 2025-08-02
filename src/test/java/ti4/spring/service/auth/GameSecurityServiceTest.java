package ti4.spring.service.auth;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.spring.exception.InvalidGameNameException;
import ti4.spring.exception.UserNotInGameForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = GameSecurityService.class)
class GameSecurityServiceTest {
    private Game game;
    private Player player;
    private ConcurrentMap<String, ManagedGame> gameMap;

    @Autowired
    private GameSecurityService service;

    @BeforeEach
    void setup() throws Exception {
        game = new Game();
        game.setName("gameSec");
        player = new Player("uid", "user", game);
        game.setPlayers(Map.of("uid", player));

        Field f = GameManager.class.getDeclaredField("gameNameToManagedGame");
        f.setAccessible(true);
        gameMap = (ConcurrentMap<String, ManagedGame>) f.get(null);
        gameMap.put("gameSec", new ManagedGame(game));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("uid", null));
        SecurityContextHolder.setContext(context);
        RequestContext.setGame(game);
    }

    @AfterEach
    void cleanup() {
        gameMap.clear();
        RequestContext.clearContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void throwsForInvalidGame() {
        gameMap.remove("gameSec");
        assertThatThrownBy(() -> service.canAccessGame("bad"))
            .isInstanceOf(InvalidGameNameException.class);
    }

    @Test
    void throwsWhenUserNotInGame() {
        game.setPlayers(Map.of());
        assertThatThrownBy(() -> service.canAccessGame("gameSec"))
            .isInstanceOf(UserNotInGameForbiddenException.class);
    }

    @Test
    void returnsTrueForValidUser() {
        assertThat(service.canAccessGame("gameSec")).isTrue();
    }
}
