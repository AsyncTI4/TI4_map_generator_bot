package ti4.spring.api.webdata;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.game.Game;
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
