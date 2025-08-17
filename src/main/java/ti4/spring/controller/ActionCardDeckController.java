package ti4.spring.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.auth.RequestContext;
import ti4.spring.service.ActionCardDeckService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/game/{gameName}/action-card-deck")
public class ActionCardDeckController {

    private final ActionCardDeckService actionCardDeckService;

    @PostMapping("/shuffle")
    @PreAuthorize("@security.canAccessGame(#gameName)")
    public ResponseEntity<String> shuffle(@PathVariable String gameName) {
        actionCardDeckService.shuffle(RequestContext.getGame(), RequestContext.getPlayer());
        return ResponseEntity.ok("Shuffled the action card deck.");
    }
}
