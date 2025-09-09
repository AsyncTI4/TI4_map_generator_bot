package ti4.spring.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import ti4.executors.ExecutionLockManager;
import ti4.map.persistence.GameManager;

@Component
public class GameLockAndRequestContextInterceptor implements HandlerInterceptor {

    private static final List<String> MUTATION_METHODS = List.of("PUT", "POST", "PATCH", "DELETE");

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String gameName = getGameNameFromUri(request);
        if (gameName == null) return true;
        if (!GameManager.isValid(gameName)) throw new InvalidGameNameException(gameName);

        boolean isWrite = MUTATION_METHODS.contains(request.getMethod());
        lockGame(gameName, isWrite);
        setupGameRequestContext(gameName);

        return true;
    }

    private String getGameNameFromUri(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, String> uriTemplateVars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        return uriTemplateVars.get("gameName");
    }

    private void setupGameRequestContext(String gameName) {
        var game = GameManager.getManagedGame(gameName).getGame();
        RequestContext.setGame(game);
    }

    private static void lockGame(String gameName, boolean isWrite) {
        var lockType = isWrite ? ExecutionLockManager.LockType.WRITE : ExecutionLockManager.LockType.READ;
        ExecutionLockManager.lock(gameName, lockType);
    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            Exception exception) {
        var game = RequestContext.getGame();
        if (game == null) return;

        boolean isWrite = MUTATION_METHODS.contains(request.getMethod());
        if (exception == null && isWrite) {
            var player = RequestContext.getPlayer();
            GameManager.save(RequestContext.getGame(), player.getUserName() + " called " + request.getRequestURI());
        }

        unlockGame(game.getName(), isWrite);
        RequestContext.clearContext();
    }

    private static void unlockGame(String gameName, boolean isWrite) {
        var lockType = isWrite ? ExecutionLockManager.LockType.WRITE : ExecutionLockManager.LockType.READ;
        ExecutionLockManager.unlock(gameName, lockType);
    }
}
