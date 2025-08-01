package ti4.spring.service.auth;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.context.SecurityContextHolder;
import ti4.map.Game;
import ti4.map.Player;

public class RequestContext {

    // TODO: Debate combining this with the Command/Button processing context
    private static final ThreadLocal<Game> game = new ThreadLocal<>();

    @NotNull
    static String getUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    static void setGame(Game game) {
        RequestContext.game.set(game);
    }

    public static Game getGame() {
        return game.get();
    }

    public static Player getPlayer() {
        return getGame().getPlayer(getUserId());
    }

    static void clearContext() {
        game.remove();
    }
}
