package ti4.spring.api.actioncard;

import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

@Service
public class ActionCardDeckService {

    public static void shuffle(Game game, Player player) {
        game.shuffleActionCards();
        String playerRepresentation = player.getRepresentationNoPing();
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(), playerRepresentation + " shuffled the action card deck.");
    }
}
