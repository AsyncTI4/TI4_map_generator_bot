package ti4.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.ActionCardModel;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class ActionCardTargets {

    private static final Set<String> AIMED_AT_THE_COMBAT_OPPONENT = Set.of(
            "courageous", "intercept", "rout", "disable", "reflective", "waylay", "scramble", "experimental", "parley");

    public static void record(Game game, String cardName, Player player, Player target) {
        if (player == null || target == null || player == target) return;
        game.getGameStats().recordAcTarget(cardName, player, target);
        GameSubEvent.ActionCardTargeted targeted =
                new GameSubEvent.ActionCardTargeted(player.getFaction(), cardName, target.getFaction());
        if (GameEventDraft.stage(game, targeted)) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardName", cardName);
        payload.put("targetFaction", target.getFaction());
        GameEventService.commit(game, GameEventType.ACTION_CARD_TARGETED, player, payload);
    }

    static void recordOnPlay(Game game, Player player, ActionCardModel card) {
        if (!AIMED_AT_THE_COMBAT_OPPONENT.contains(card.getAlias())) return;
        combatOpponent(game, player).ifPresent(opponent -> record(game, card.getName(), player, opponent));
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
