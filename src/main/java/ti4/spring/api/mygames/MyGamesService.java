package ti4.spring.api.mygames;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;

@RequiredArgsConstructor
@Service
public class MyGamesService {

    public List<MyGameSummary> getMyGames(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) return Collections.emptyList();

        return managedPlayer.getGames().stream()
                .filter(ManagedGame::isActive)
                .map(game -> toSummary(game, userId))
                .filter(Objects::nonNull)
                .toList();
    }

    private MyGameSummary toSummary(ManagedGame managedGame, String userId) {
        Game game = managedGame.getGame();
        if (game == null) return null;
        Player player = game.getPlayer(userId);
        if (player == null) return null;
        return new MyGameSummary(game.getName(), player.getFaction(), player.getColorID());
    }
}
