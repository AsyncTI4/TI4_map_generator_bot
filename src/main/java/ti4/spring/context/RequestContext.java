package ti4.spring.context;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.context.SecurityContextHolder;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class RequestContext {

    // TODO: Debate combining this with the Command/Button processing context
    private static final ThreadLocal<Game> game = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> saveGame = ThreadLocal.withInitial(() -> true);

    @NotNull
    public static String getUserId() {
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

    static boolean shouldSaveGame() {
        return saveGame.get();
    }

    static void setSaveGame(boolean saveGame) {
        RequestContext.saveGame.set(saveGame);
    }

    static void clearContext() {
        game.remove();
        saveGame.remove();
    }
}
