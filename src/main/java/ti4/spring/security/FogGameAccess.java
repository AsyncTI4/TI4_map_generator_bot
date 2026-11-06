package ti4.spring.security;

import javax.annotation.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ti4.game.Game;
import ti4.game.Player;

public record FogGameAccess(Role role, @Nullable Player seat) {

    public enum Role {
        ANONYMOUS,
        OUTSIDER,
        PLAYER,
        GAME_MASTER
    }

    public static FogGameAccess forCurrentUser(Game game) {
        return of(game, currentUserId());
    }

    public static FogGameAccess of(Game game, @Nullable String userId) {
        if (userId == null) {
            return new FogGameAccess(Role.ANONYMOUS, null);
        }
        Player player = game.getPlayer(userId);
        if (holdsSeat(player)) {
            return new FogGameAccess(Role.PLAYER, player);
        }
        if (isGameMaster(game, userId)) {
            return new FogGameAccess(Role.GAME_MASTER, null);
        }
        return new FogGameAccess(Role.OUTSIDER, null);
    }

    public static boolean holdsSeat(@Nullable Player player) {
        return player != null && player.isRealPlayer() && !player.isNeutral();
    }

    @Nullable
    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }
        return null;
    }

    private static boolean isGameMaster(Game game, String userId) {
        return userId.equals(game.getOwnerID())
                || game.getPlayersWithGMRole().stream().anyMatch(player -> userId.equals(player.getUserID()));
    }
}
