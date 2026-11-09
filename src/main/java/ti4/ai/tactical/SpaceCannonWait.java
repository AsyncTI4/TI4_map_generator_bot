package ti4.ai.tactical;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSeats;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.service.combat.SpaceCannonOffenceWindow;

@UtilityClass
class SpaceCannonWait {

    static final String EXPECTED_KEY = "spaceCannonExpectedSince|";
    static final long HUMAN_GRACE_MILLIS = 10 * 60_000L;
    static final long AI_GRACE_MILLIS = 60_000L;
    private static final List<String> AFTER_MOVEMENT_PREFIXES =
            List.of("landUnits_", "doneLanding_", "tacticalActionBuild_", "doneWithTacticalAction");

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> turn, Tile tile) {
        if (!movementFinished(context, turn)) return Optional.empty();
        List<Player> pending = pending(context.game(), context.seat(), tile);
        if (pending.isEmpty()) return Optional.empty();
        String key = EXPECTED_KEY + TacticalRules.actionKey(context);
        long since = context.memory().get(key).map(Long::parseLong).orElse(context.now());
        if (!context.memory().has(key)) context.memory().put(key, String.valueOf(since));
        long grace = pending.stream().allMatch(AiSeats::isAiSeat) ? AI_GRACE_MILLIS : HUMAN_GRACE_MILLIS;
        if (context.now() - since < grace) {
            return Optional.of(new AiDecision.Wait(since + grace, "space cannon offense from " + names(pending)));
        }
        return turn.stream()
                .filter(prompt -> prompt.buttons().stream().anyMatch(button -> button.isOwnedBy(context.faction())))
                .findFirst()
                .map(prompt -> new AiDecision.Unsure(
                        prompt, names(pending) + " never fired or declined space cannon offense at its ships"));
    }

    static List<Player> pending(Game game, Player seat, Tile tile) {
        if (game.isL1Hero() || tile.isScar(game) || !BoardView.hasOwnShips(seat, tile)) return List.of();
        Set<String> resolved = SpaceCannonOffenceWindow.resolvedBy(game, tile.getPosition());
        return ButtonHelper.getPlayersWithPds2Cover(seat, game, tile.getPosition()).stream()
                .filter(covering -> covering != seat)
                .filter(covering -> !seat.getAllianceMembers().contains(covering.getFaction()))
                .filter(covering -> !resolved.contains(covering.getFaction()))
                .toList();
    }

    private static boolean movementFinished(AiTurnContext context, List<AiPrompt> turn) {
        return Prompts.owned(
                        turn,
                        context.faction(),
                        id -> AFTER_MOVEMENT_PREFIXES.stream().anyMatch(id::startsWith))
                .isPresent();
    }

    private static String names(List<Player> players) {
        return players.stream().map(Player::getFaction).collect(Collectors.joining(", "));
    }
}
