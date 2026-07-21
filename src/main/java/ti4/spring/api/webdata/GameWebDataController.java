package ti4.spring.api.webdata;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.context.RequestContext;
import ti4.spring.service.gameevent.GameEventDto;
import ti4.spring.service.gameevent.GameEventService;
import ti4.website.model.WebGameState;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/game/{gameName}")
public class GameWebDataController {

    private final GameWebDataService gameWebDataService;
    private final GameEventService gameEventService;

    @GetMapping(value = "/web-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@PathVariable String gameName) {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        if (game.isFowMode()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gameWebDataService.getOrCompute(gameName));
    }

    /**
     * Fog-of-War aware web-data endpoint. GM/owner gets the full unfiltered payload (or,
     * with {@code asPlayer}, a preview of another player's fogged view for debugging).
     * Participants always get their own fogged view. Everyone else is forbidden.
     */
    @GetMapping(value = "/web-data-fow", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFow(
            @PathVariable String gameName, @RequestParam(required = false) String asPlayer) {
        Game game = RequestContext.getGame();
        if (game == null || !game.isFowMode()) {
            return ResponseEntity.notFound().build();
        }

        String userId = getOptionalUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required to view Fog of War games");
        }

        boolean isGm = userId.equals(game.getOwnerID())
                || game.getPlayersWithGMRole().stream().anyMatch(p -> userId.equals(p.getUserID()));

        if (isGm) {
            if (asPlayer == null || asPlayer.isBlank()) {
                return withViewerIsGmHeader(gameWebDataService.getOrCompute(gameName), true);
            }
            Player target = resolvePlayer(game, asPlayer);
            if (target == null) {
                return ResponseEntity.notFound().build();
            }
            return withViewerIsGmHeader(gameWebDataService.computeFiltered(game, target), true);
        }

        Player viewer = game.getPlayer(userId);
        if (viewer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You must be participating in this game to view it");
        }
        return withViewerIsGmHeader(gameWebDataService.computeFiltered(game, viewer), false);
    }

    /**
     * Whether the requesting user is GM/owner can't be derived from the response body alone: a GM
     * previewing another player's view and that player's own real view are shaped identically. Callers
     * that need to distinguish them (e.g. to keep showing "view as" controls while previewing) read this
     * header instead.
     */
    private ResponseEntity<String> withViewerIsGmHeader(String body, boolean isGm) {
        return ResponseEntity.ok()
                .header("X-Viewer-Is-Gm", String.valueOf(isGm))
                .body(body);
    }

    private Player resolvePlayer(Game game, String idOrFaction) {
        Player byUserId = game.getPlayer(idOrFaction);
        if (byUserId != null) {
            return byUserId;
        }
        return game.getPlayerFromColorOrFaction(idOrFaction);
    }

    private String getOptionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return null;
    }

    @GetMapping(value = "/game-state", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebGameState> getGameState() {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        if (game.isFowMode()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(WebGameState.fromGame(game));
    }

    @GetMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<GameEventDto>> getEvents() {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        if (game.isFowMode()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gameEventService.getEvents(game));
    }
}
