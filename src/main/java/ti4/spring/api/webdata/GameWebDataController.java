package ti4.spring.api.webdata;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ti4.game.Game;
import ti4.spring.context.RequestContext;
import ti4.spring.security.FogGameAccess;
import ti4.spring.service.gameevent.GameEventDto;
import ti4.spring.service.gameevent.GameEventService;
import ti4.website.model.WebGameState;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/game/{gameName}")
public class GameWebDataController {

    private final GameWebDataService gameWebDataService;
    private final FogWebDataService fogWebDataService;
    private final GameEventService gameEventService;

    @GetMapping(value = "/web-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@PathVariable String gameName, @RequestParam(required = false) String viewAs) {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        if (game.isFowMode()) {
            return getFogWebData(game, viewAs);
        }
        return ResponseEntity.ok(gameWebDataService.getOrCompute(gameName));
    }

    private ResponseEntity<String> getFogWebData(Game game, String viewAs) {
        FogGameAccess access = FogGameAccess.forCurrentUser(game);
        return switch (access.role()) {
            case ANONYMOUS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case OUTSIDER -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case PLAYER ->
                ResponseEntity.ok(fogWebDataService.forSeat(game, access.seat().getFaction()));
            case GAME_MASTER ->
                fogWebDataService
                        .forGameMaster(game, viewAs)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.badRequest().build());
        };
    }

    @GetMapping(value = "/game-state", produces = MediaType.APPLICATION_JSON_VALUE)
    public static ResponseEntity<WebGameState> getGameState() {
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
    public ResponseEntity<List<GameEventDto>> getEvents(@RequestParam(required = false) Long since) {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        if (game.isFowMode()) {
            return ResponseEntity.notFound().build();
        }
        if (since == null) {
            return ResponseEntity.ok(gameEventService.getEvents(game));
        }
        return ResponseEntity.ok(gameEventService.getEventsSince(game, since));
    }
}
