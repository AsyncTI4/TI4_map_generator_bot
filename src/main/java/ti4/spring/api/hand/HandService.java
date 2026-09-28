package ti4.spring.api.hand;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import ti4.game.Player;

@Service
public class HandService {

    public static Set<String> getActionCards(Player player) {
        return new HashSet<>(player.getActionCards().keySet());
    }

    public static Set<String> getSecretObjectives(Player player) {
        return new HashSet<>(player.getSecrets().keySet());
    }

    public static Set<String> getPromissoryNotes(Player player) {
        return new HashSet<>(player.getPromissoryNotes().keySet());
    }
}
