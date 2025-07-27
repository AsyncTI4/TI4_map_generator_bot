package ti4.spring.controller;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.model.GetHandResponse;
import ti4.spring.service.HandService;
import ti4.spring.service.auth.RequestContext;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/game/{gameName}/hand")
public class HandController {

    private final HandService handService;

    @GetMapping
    @PreAuthorize("@security.canAccessGame(#gameName)")
    public GetHandResponse get(@PathVariable String gameName) {
        var player = RequestContext.getPlayer();

        Set<String> actionCards = handService.getActionCards(player);
        Set<String> secretObjectives = handService.getSecretObjectives(player);
        Set<String> promissoryNotes = handService.getPromissoryNotes(player);

        return new GetHandResponse(actionCards, secretObjectives, promissoryNotes);
    }
}
