package ti4.spring.context;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.spring.service.deploy.ActiveLeaseService;

@Component
@lombok.RequiredArgsConstructor
public class GameLockAndRequestContextInterceptor implements HandlerInterceptor {

    private static final List<String> MUTATION_METHODS = List.of("PUT", "POST", "PATCH", "DELETE");
    private final ActiveLeaseService activeLeaseService;

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        String gameName = getGameNameFromUri(request);
        if (gameName == null) return true;
        if (!GameManager.isValid(gameName)) throw new InvalidGameNameException(gameName);

        boolean shouldSaveGame = shouldSaveGame(request, handler);
        if (shouldSaveGame && !activeLeaseService.mayMutate()) {
            rejectInactiveMutation(response);
            return false;
        }

        if (!shouldSetupGameRequestContext(handler)) return true;

        setupGameRequestContext(gameName, shouldSaveGame);
        return true;
    }

    private String getGameNameFromUri(ServletRequest request) {
        Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attributes instanceof Map<?, ?> vars)) {
            return null;
        }
        Object gameNameObject = vars.get("gameName");
        return (gameNameObject instanceof String gameName) ? gameName : null;
    }

    private boolean shouldSaveGame(HttpServletRequest request, Object handler) {
        boolean shouldSaveGame = MUTATION_METHODS.contains(request.getMethod());
        if (handler instanceof HandlerMethod handlerMethod) {
            SetupRequestContext annotation = handlerMethod.getMethodAnnotation(SetupRequestContext.class);
            if (annotation != null) {
                if (!annotation.value()) {
                    return false;
                }
                // Only save if both the method is a mutation and the annotation allows saving.
                shouldSaveGame &= annotation.save();
            }
        }
        return shouldSaveGame;
    }

    private boolean shouldSetupGameRequestContext(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            SetupRequestContext annotation = handlerMethod.getMethodAnnotation(SetupRequestContext.class);
            return annotation == null || annotation.value();
        }
        return true;
    }

    private void setupGameRequestContext(String gameName, boolean shouldSaveGame) {
        lockGame(gameName, shouldSaveGame);
        try {
            var game = GameManager.getManagedGame(gameName).getGame();
            if (game == null) throw new RuntimeException("Unable to load game: " + gameName);
            RequestContext.setGame(game);
            RequestContext.setSaveGame(shouldSaveGame);
        } catch (Exception e) {
            unlockGame(gameName, shouldSaveGame);
            throw e;
        }
    }

    private static void rejectInactiveMutation(HttpServletResponse response) {
        try {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service temporarily unavailable: bot is not active");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void lockGame(String gameName, boolean shouldSaveGame) {
        var lockType = shouldSaveGame ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutionLockManager.lock(gameName, lockType);
    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            Exception exception) {
        try {
            var game = RequestContext.getGame();
            if (game != null) {
                if (exception == null && RequestContext.shouldSaveGame()) {
                    if (activeLeaseService.mayMutate()) {
                        var player = RequestContext.getPlayer();
                        GameManager.save(game, player.getUserName() + " called " + request.getRequestURI());
                    } else {
                        BotLogger.warning(
                                "Skipped web mutation save because this instance no longer owns the active lease. "
                                        + request.getRequestURI());
                    }
                }

                unlockGame(game.getName(), RequestContext.shouldSaveGame());
            }
        } finally {
            RequestContext.clearContext();
        }
    }

    private static void unlockGame(String gameName, boolean shouldSaveGame) {
        var lockType = shouldSaveGame ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        ExecutionLockManager.unlock(gameName, lockType);
    }
}
