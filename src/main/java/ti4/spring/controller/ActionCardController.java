package ti4.spring.controller;

import java.util.ArrayList;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.spring.model.GetPlayerActionCards;
import ti4.spring.service.auth.DiscordOAuthService;
import ti4.spring.validator.GameNameValidator;
import ti4.spring.validator.UserIdValidator;

@AllArgsConstructor
@RestController
@RequestMapping("/api/action-cards")
public class ActionCardController {

    private final DiscordOAuthService discordOAuthService;

    @PostMapping("/game/{gameName}/shuffle")
    public ResponseEntity<String> shuffle(@PathVariable String gameName) {
        GameNameValidator.validate(gameName);
        var game = GameManager.getManagedGame(gameName).getGame();
        game.shuffleActionCards();
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Shuffled the action card deck.");
        return ResponseEntity.ok("Shuffled the action card deck.");
    }

    @GetMapping("/game/{gameName}/{userId}")
    public GetPlayerActionCards getHand(
            @PathVariable String gameName,
            @PathVariable String userId,
            @RequestHeader(value = "Authorization") String authorizationHeader) {
        GameNameValidator.validate(gameName);
        UserIdValidator.validate(gameName, userId);

        discordOAuthService.authorize(authorizationHeader, userId);

        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = game.getPlayer(userId);

        var actionCards = new ArrayList<String>();
        player.getActionCards().forEach((key, value) -> {
            for (int i = 0; i < value; i++) {
                actionCards.add(key);
            }
        });

        return new GetPlayerActionCards(actionCards);
    }
}
