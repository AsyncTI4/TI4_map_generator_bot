package ti4.image.playerarea;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;

final class GuildShipPartners {

    private static final List<String> CONVOY_NOTES =
            List.of("convoys", "sigma_trade_convoys", "viability_trade_convoys");

    private GuildShipPartners() {}

    static Set<Player> of(Game game, Player player, Set<Player> neighbours) {
        Set<Player> partners = new LinkedHashSet<>();
        for (Player other : game.getRealPlayers()) {
            if (player == other || neighbours.contains(other)) {
                continue;
            }
            if (game.isAgeOfCommerceMode()
                    || player.hasAbility("guild_ships")
                    || other.hasAbility("guild_ships")
                    || hasConvoys(player)
                    || hasConvoys(other)) {
                partners.add(other);
            }
        }
        return partners;
    }

    private static boolean hasConvoys(Player player) {
        return CONVOY_NOTES.stream().anyMatch(player.getPromissoryNotesInPlayArea()::contains);
    }
}
