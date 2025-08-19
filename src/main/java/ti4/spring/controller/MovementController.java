package ti4.spring.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.spring.model.MovementDisplacementRequest;
import ti4.spring.service.MovementService;
import ti4.spring.service.auth.RequestContext;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/game/{gameName}/movement")
public class MovementController {

    private final MovementService movementService;

    /**
     * Single endpoint: receives target and full displacement from multiple sources,
     * applies movement, places CC if needed, and posts eventless tile UI.
     */
    @PostMapping
    @PreAuthorize("@security.canAccessGame(#gameName)")
    public ResponseEntity<String> commit(
            @PathVariable String gameName, @RequestBody MovementDisplacementRequest request) {
        Game game = RequestContext.getGame();
        Player player = RequestContext.getPlayer();

        Tile tile = movementService.commitMovement(game, player, request.targetPosition(), request.displacement());
        if (tile == null) return ResponseEntity.badRequest().body("Target system not found.");
        return ResponseEntity.ok("Movement staged.");
    }
}
