package ti4.spring.api.overlay;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.spring.context.SetupRequestContext;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/game/{gameName}/overlays")
public class MapOverlayController {

    private final MapOverlayService mapOverlayService;

    @SetupRequestContext(false)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MapOverlayResponse> get(@PathVariable String gameName) {
        ManagedGame managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null || managedGame.isFowMode()) {
            return ResponseEntity.notFound().build();
        }
        return mapOverlayService
                .getOverlays(gameName)
                .map(overlays ->
                        ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(overlays))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
