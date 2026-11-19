package ti4.helpers;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.ActionCardModel;

@UtilityClass
public class ActionCardTargets {

    private static final Set<String> AIMED_AT_THE_COMBAT_OPPONENT = Set.of(
            "courageous", "intercept", "rout", "disable", "reflective", "waylay", "scramble", "experimental", "parley");

    static void recordOnPlay(Game game, Player player, ActionCardModel card) {
        if (!AIMED_AT_THE_COMBAT_OPPONENT.contains(card.getAlias())) return;
        combatOpponent(game, player)
                .ifPresent(opponent -> game.getGameStats().recordAcTarget(card.getName(), player, opponent));
    }

    static Optional<Player> combatOpponent(Game game, Player player) {
        Player active = game.getActivePlayer();
        if (active == null) return Optional.empty();
        if (active != player) return Optional.of(active);
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) return Optional.empty();
        List<Player> others = game.getRealPlayers().stream()
                .filter(other -> other != player && tile.containsPlayersUnits(other))
                .toList();
        return others.size() == 1 ? Optional.of(others.getFirst()) : Optional.empty();
    }
}
