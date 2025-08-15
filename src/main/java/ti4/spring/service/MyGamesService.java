package ti4.spring.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.spring.model.MyGameSummary;

@RequiredArgsConstructor
@Service
public class MyGamesService {

    public List<MyGameSummary> getMyGames(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        if (managedPlayer == null) return List.of();

        return managedPlayer.getGames().stream()
                .map(game -> toSummary(game, userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private MyGameSummary toSummary(ManagedGame managedGame, String userId) {
        Game game = managedGame.getGame();
        if (game == null) return null;
        Player player = game.getPlayer(userId);
        if (player == null) return null;
        return new MyGameSummary(game.getName(), player.getFaction(), player.getColorID());
    }
}
