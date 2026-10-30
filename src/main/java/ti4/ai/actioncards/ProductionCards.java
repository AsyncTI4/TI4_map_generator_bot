package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ProductionCards {

    static final String WAR_MACHINE = "war_machine";
    static final List<String> WAR_MACHINES = List.of("war_machine1", "war_machine2", "war_machine3", "war_machine4");

    public enum PlayState {
        UNPLAYED,
        PENDING,
        SETTLED
    }

    static AiCard warMachine() {
        return AiActionCards.production(WAR_MACHINE, WAR_MACHINES::contains);
    }

    public static boolean holdsWarMachine(Game game, Player seat) {
        return heldWarMachine(seat).isPresent() && ActionCardRules.mayPlayNow(game, seat);
    }

    public static Optional<AiDecision> playWarMachine(AiTurnContext context, String scope) {
        String key = PlayMemory.productionKey(scope);
        if (context.memory().has(key) || !ActionCardRules.mayPlayNow(context.game(), context.seat())) {
            return Optional.empty();
        }
        return heldWarMachine(context.seat())
                .flatMap(alias -> ActionCardRules.startPlay(context, key, alias, Plan.of(0)));
    }

    public static PlayState warMachine(AiTurnContext context, String scope) {
        return context.memory()
                .get(PlayMemory.productionKey(scope))
                .flatMap(CardPlay::decode)
                .map(play -> play.active() ? PlayState.PENDING : PlayState.SETTLED)
                .orElse(PlayState.UNPLAYED);
    }

    private static Optional<String> heldWarMachine(Player seat) {
        return seat.getActionCards().keySet().stream()
                .filter(WAR_MACHINES::contains)
                .sorted()
                .findFirst();
    }
}
