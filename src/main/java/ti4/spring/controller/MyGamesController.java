package ti4.spring.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.auth.RequestContext;
import ti4.spring.model.MyGameSummary;
import ti4.spring.service.MyGamesService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/my-games")
public class MyGamesController {

    private final MyGamesService myGamesService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MyGameSummary> get() {
        String userId = RequestContext.getUserId();
        return myGamesService.getMyGames(userId);
    }
}
