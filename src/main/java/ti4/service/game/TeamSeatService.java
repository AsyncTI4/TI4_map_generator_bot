package ti4.service.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

@UtilityClass
public class TeamSeatService {

    public record FogSeat(Game game, Player seat) {}

    public static List<FogSeat> findSharedActiveFogSeats(String targetId, String viewerId) {
        List<FogSeat> seats = new ArrayList<>();
        List<ManagedGame> candidates = GameManager.getManagedGames().stream()
                .filter(managedGame -> managedGame.isFowMode() && !managedGame.isHasEnded())
                .filter(managedGame ->
                        managedGame.hasPlayerOrTeamMate(targetId) && managedGame.hasPlayerOrTeamMate(viewerId))
                .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
                .toList();
        for (ManagedGame managedGame : candidates) {
            Game game = managedGame.getGame();
            Player seat = sharedSeat(game, targetId, viewerId);
            if (seat != null) {
                seats.add(new FogSeat(game, seat));
            }
        }
        return seats;
    }

    public static Player seatOf(Game game, String userId) {
        Player primarySeat = game.getPlayer(userId);
        if (primarySeat != null && !primarySeat.isSpectator()) {
            return primarySeat;
        }
        return game.getPlayers().values().stream()
                .filter(player -> !player.getUserID().equals(userId))
                .filter(player -> player.getTeamMateIDs().contains(userId))
                .findFirst()
                .orElse(primarySeat);
    }

    static Player sharedSeat(Game game, String targetId, String viewerId) {
        Player seat = seatOf(game, targetId);
        if (seat == null || seat.isSpectator() || seat != seatOf(game, viewerId)) {
            return null;
        }
        return seat;
    }
}
