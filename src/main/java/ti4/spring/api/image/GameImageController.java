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

@RequiredArgsConstructor
@RestController
// TODO: thus should be /image
@RequestMapping("/api/public/game/{gameName}")
public class GameImageController {

    // TODO: once the above is /image, this doesn't need to specify anything
    @GetMapping("/image")
    public ResponseEntity<String> get(@PathVariable String gameName) {
        String lastImageFileName = RequestContext.getGame().getLastImageFileName();
        if (isBlank(lastImageFileName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(lastImageFileName);
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@PathVariable String gameName) {
        Game game = RequestContext.getGame();
        MapRenderPipeline.queue(game, null, DisplayType.all, null);
        return ResponseEntity.ok("Queued");
    }
}
