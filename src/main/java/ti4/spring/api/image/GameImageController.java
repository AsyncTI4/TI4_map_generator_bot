package ti4.spring.api.image;

import static software.amazon.awssdk.utils.StringUtils.isBlank;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.spring.context.RequestContext;
import ti4.spring.context.SetupRequestContext;

@RequiredArgsConstructor
@RestController
// TODO: this should be /image
@RequestMapping("/api/public/game/{gameName}")
public class GameImageController {

    private final GameImageService gameImageService;

    // TODO: once the above is /image, this doesn't need to specify anything
    @SetupRequestContext(false)
    @GetMapping("/image")
    public ResponseEntity<String> get(@PathVariable String gameName) {
        String latestMapImageName = gameImageService.getLatestMapImageName(gameName);
        if (isBlank(latestMapImageName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latestMapImageName);
    }

    @SetupRequestContext(save = false)
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@PathVariable String gameName) {
        Game game = RequestContext.getGame();
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        MapRenderPipeline.queue(game, null, DisplayType.all, null);
        return ResponseEntity.ok("Queued");
    }
}
