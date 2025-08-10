package ti4.spring.service;

import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@Service
public class ActionCardDeckService {

    public void shuffle(Game game, Player player) {
        game.shuffleActionCards();
        String playerRepresentation = player.getRepresentationNoPing();
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(), playerRepresentation + " shuffled the action card deck.");
    }
}
