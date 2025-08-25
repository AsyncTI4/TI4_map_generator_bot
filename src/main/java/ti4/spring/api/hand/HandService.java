package ti4.spring.api.hand;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import ti4.map.Player;

@Service
public class HandService {

    public Set<String> getActionCards(Player player) {
        return new HashSet<>(player.getActionCards().keySet());
    }

    public Set<String> getSecretObjectives(Player player) {
        return new HashSet<>(player.getSecrets().keySet());
    }

    public Set<String> getPromissoryNotes(Player player) {
        return new HashSet<>(player.getPromissoryNotes().keySet());
    }
}
