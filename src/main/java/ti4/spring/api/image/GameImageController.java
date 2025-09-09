package ti4.spring.api.image;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
}
