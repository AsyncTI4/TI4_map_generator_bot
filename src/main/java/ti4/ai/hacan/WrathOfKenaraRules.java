package ti4.ai.hacan;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class WrathOfKenaraRules {

    private static final String FLAGSHIP = "hacan_flagship";
    private static final String FLAGSHIP_TEXT = "Wrath of Kenara";
    private static final String ADD_PREFIX = "hacanFlagship_";
    public static final String FINISH_PREFIX = "hacanFlagshipFinish_";
    private static final String DECLINE = "deleteButtons";

    record Offer(String position, int eligible, int hitsSoFar) {}

    record Add(PromptButton button, int hits) {}

    public static Optional<AiDecision> next(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasUnit(FLAGSHIP)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || !prompt.content().contains(FLAGSHIP_TEXT)) continue;
            List<Add> adds = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(ADD_PREFIX))
                    .map(button -> new Add(button, number(button.handlerId(), ADD_PREFIX, 2)))
                    .toList();
            Optional<PromptButton> finish = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(FINISH_PREFIX));
            if (adds.isEmpty() && finish.isEmpty()) continue;
            Optional<Offer> offer = offer(adds, finish);
            if (offer.isEmpty()) continue;
            return decide(context, prompt, offer.get(), adds, finish);
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> decide(
            AiTurnContext context, AiPrompt prompt, Offer offer, List<Add> adds, Optional<PromptButton> finish) {
        Tile tile = context.game().getTileByPosition(offer.position());
        int wanted = tile == null ? 0 : wantedHits(context.game(), context.seat(), tile, offer.eligible());
        if (offer.hitsSoFar() < wanted) {
            Optional<Add> closer = adds.stream()
                    .filter(add -> add.hits() > offer.hitsSoFar() && add.hits() <= wanted)
                    .max(Comparator.comparingInt(Add::hits));
            if (closer.isPresent()) {
                return Optional.of(AiDecision.press(
                        prompt,
                        closer.get().button(),
                        "spend trade goods to turn misses into hits with Wrath of Kenara"));
            }
        }
        if (offer.hitsSoFar() > 0 && offer.hitsSoFar() <= wanted && finish.isPresent()) {
            return Optional.of(AiDecision.press(prompt, finish.get(), "score the Wrath of Kenara hits"));
        }
        if (offer.hitsSoFar() > wanted) {
            Optional<PromptButton> startOver = prompt.firstEnabled(button -> button.isUnowned()
                    && button.handlerId().startsWith(ADD_PREFIX)
                    && number(button.handlerId(), ADD_PREFIX, 2) == 0);
            if (startOver.isPresent()) {
                return Optional.of(AiDecision.press(prompt, startOver.get(), "start the Wrath of Kenara choice over"));
            }
        }
        return prompt.firstEnabled(button -> button.isUnowned() && DECLINE.equals(button.handlerId()))
                .map(button ->
                        AiDecision.press(prompt, button, "keep its trade goods instead of using Wrath of Kenara"));
    }

    private static Optional<Offer> offer(List<Add> adds, Optional<PromptButton> finish) {
        if (!adds.isEmpty()) {
            String id = adds.getFirst().button().handlerId();
            String position = StringUtils.substringBetween(id, ADD_PREFIX, "_");
            int eligible = number(id, ADD_PREFIX, 1) + number(id, ADD_PREFIX, 2);
            int hitsSoFar = finish.map(button -> number(button.handlerId(), FINISH_PREFIX, 1))
                    .orElse(0);
            return position == null ? Optional.empty() : Optional.of(new Offer(position, eligible, hitsSoFar));
        }
        String id = finish.orElseThrow().handlerId();
        String position = StringUtils.substringBetween(id, FINISH_PREFIX, "_");
        int hits = number(id, FINISH_PREFIX, 1);
        return position == null ? Optional.empty() : Optional.of(new Offer(position, hits, hits));
    }

    static int wantedHits(Game game, Player seat, Tile tile, int eligible) {
        int spare = Math.min(eligible, seat.getTg());
        while (spare > 0
                && ScoringReserve.planAfterReserve(game, seat, SpendCost.tradeGoods(spare))
                        .isEmpty()) {
            spare--;
        }
        return Math.min(spare, enemyHitPoints(game, seat, tile));
    }

    private static int enemyHitPoints(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        int points = 0;
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (Map.Entry<UnitType, Integer> ship :
                    BoardView.ships(space, other).entrySet()) {
                points += ship.getValue();
                boolean sustains = BoardView.model(other, ship.getKey())
                        .map(UnitModel::getSustainDamage)
                        .orElse(false);
                if (sustains) points += BoardView.undamaged(space, other, ship.getKey());
            }
        }
        return points;
    }

    private static int number(String handlerId, String prefix, int index) {
        String[] parts = StringUtils.removeStart(handlerId, prefix).split("_");
        if (index >= parts.length || !StringUtils.isNumeric(parts[index])) return 0;
        return Integer.parseInt(parts[index]);
    }
}
