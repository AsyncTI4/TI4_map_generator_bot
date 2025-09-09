package ti4.spring.api.image;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.map.persistence.GameLoadService;

@RequiredArgsConstructor
@RestController
public class GameImageController {

    private final GameImageService gameImageService;

    @GetMapping("/api/public/game/{gameName}/image")
    public ResponseEntity<String> get(@PathVariable String gameName) {
        String last = gameImageService.getLastImage(gameName);
        if (last == null || last.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(last);
    }

    @PostMapping("/api/public/game/{gameName}/refresh")
    public ResponseEntity<String> refresh(@PathVariable String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        MapRenderPipeline.queue(game, null, DisplayType.all, null);
        return ResponseEntity.ok("Queued");
    }
}
