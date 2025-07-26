package ti4.spring.service.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import ti4.map.Player;

@Service
public class ActionCardService {

    public List<String> getHand(Player player) {
        var actionCards = new ArrayList<String>();
        player.getActionCards().forEach((actionCardId, count) -> {
            for (int i = 0; i < count; i++) {
                actionCards.add(actionCardId);
            }
        });
        return actionCards;
    }
}
