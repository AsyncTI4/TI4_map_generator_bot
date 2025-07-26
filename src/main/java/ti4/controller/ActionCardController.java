package ti4.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.controller.validator.GameNameValidator;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;

@RestController
@RequestMapping("/api/action-cards")
public class ActionCardController {

    @PostMapping("/game/{gameName}/shuffle")
    public ResponseEntity<String> shuffle(@PathVariable String gameName) {
        GameNameValidator.validate(gameName);
        var game = GameManager.getManagedGame(gameName).getGame();
        game.shuffleActionCards();
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Shuffled the action card deck.");
        return ResponseEntity.ok("Shuffled the action card deck.");
    }

    @GetMapping("/game/{gameName}/{player}")
    public ResponseEntity<String> getPlayerHand(@PathVariable String gameName, @PathVariable String player) {
        GameNameValidator.validate(gameName);
        var game = GameManager.getManagedGame(gameName).getGame();
        game.shuffleActionCards();
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Shuffled the action card deck.");
        return ResponseEntity.ok("Shuffled the action card deck.");
    }
}
