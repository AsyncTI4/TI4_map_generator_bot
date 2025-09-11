package ti4.spring.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
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

        setupGameRequestContext(gameName, request, handler);
        lockGame(gameName);

        return true;
    }

    private String getGameNameFromUri(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<String, String> uriTemplateVars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        return uriTemplateVars.get("gameName");
    }

    private void setupGameRequestContext(String gameName, HttpServletRequest request, Object handler) {
        boolean shouldSaveGame = MUTATION_METHODS.contains(request.getMethod());
        if (handler instanceof HandlerMethod handlerMethod) {
            SetupRequestContext annotation = handlerMethod.getMethodAnnotation(SetupRequestContext.class);
            if (annotation != null) {
                if (!annotation.value()) {
                    return;
                }
                // Only save if both the method is a mutation and the annotation allows saving.
                shouldSaveGame &= annotation.save();
            }
        }

        var game = GameManager.getManagedGame(gameName).getGame();
        RequestContext.setGame(game);
        RequestContext.setSaveGame(shouldSaveGame);
    }

    private static void lockGame(String gameName) {
        var lockType = RequestContext.shouldSaveGame()
                ? ExecutionLockManager.LockType.WRITE
                : ExecutionLockManager.LockType.READ;
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

        if (exception == null && RequestContext.shouldSaveGame()) {
            var player = RequestContext.getPlayer();
            GameManager.save(RequestContext.getGame(), player.getUserName() + " called " + request.getRequestURI());
        }

        unlockGame(game.getName());
        RequestContext.clearContext();
    }

    private static void unlockGame(String gameName) {
        var lockType = RequestContext.shouldSaveGame()
                ? ExecutionLockManager.LockType.WRITE
                : ExecutionLockManager.LockType.READ;
        ExecutionLockManager.unlock(gameName, lockType);
    }
}
