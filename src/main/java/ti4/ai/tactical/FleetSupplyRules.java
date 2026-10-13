package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class FleetSupplyRules {

    public static final String OPEN_PREFIX = "getDamageButtons_";
    private static final String REMOVE_SUFFIX = "_remove";
    private static final String REMOVE_TYPE = "remove";
    private static final String LATEST_ASSIGN_HITS = "latestAssignHits";
    private static final String ASSIGN_HITS = "assignHits";
    private static final String DONE = "deleteButtons";
    private static final String OPENED_KEY = "fleetSupplyRemoval|";
    private static final String SINGLE_UNIT = "1";

    public static Optional<AiDecision> removeExcessShips(AiTurnContext context) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        Optional<AiDecision> remove = removeCheapestShip(context, prompts);
        if (remove.isPresent()) return remove;
        Optional<AiDecision> open = openRemoval(context, prompts);
        if (open.isPresent()) return open;
        return finishRemoval(context, prompts);
    }

    private static Optional<AiDecision> removeCheapestShip(AiTurnContext context, List<AiPrompt> prompts) {
        if (!removing(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> cheapest = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && removesOneShip(seat, button))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .filter(button -> overSupplied(game, seat, positionOf(button)))
                    .filter(button -> keepsCargoAboard(game, seat, button))
                    .min(Comparator.comparingDouble(button -> CombatRules.lossCost(seat, button)));
            if (cheapest.isPresent()) {
                return Optional.of(
                        AiDecision.press(prompt, cheapest.get(), "remove its cheapest ship to fit its fleet supply"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> openRemoval(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            if (!Prompts.mentions(prompt, seat)) continue;
            Optional<PromptButton> open = prompt.firstEnabled(button -> button.isUnowned()
                    && button.handlerId().startsWith(OPEN_PREFIX)
                    && button.handlerId().endsWith(REMOVE_SUFFIX)
                    && !context.alreadyPressed(prompt, button));
            if (open.isEmpty()) continue;
            String position = StringUtils.substringBetween(open.get().handlerId(), OPEN_PREFIX, REMOVE_SUFFIX);
            if (!overSupplied(game, seat, position)) continue;
            context.memory().put(OPENED_KEY + position, String.valueOf(context.now()));
            return Optional.of(AiDecision.press(prompt, open.get(), "get back within its fleet supply"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> finishRemoval(AiTurnContext context, List<AiPrompt> prompts) {
        if (!removing(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            Optional<String> position = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && removesOneShip(seat, button))
                    .map(FleetSupplyRules::positionOf)
                    .filter(candidate -> context.memory().has(OPENED_KEY + candidate))
                    .findFirst();
            if (position.isEmpty() || overSupplied(game, seat, position.get())) continue;
            Optional<PromptButton> done =
                    prompt.firstEnabled(button -> button.isUnowned() && DONE.equals(button.handlerId()));
            if (done.isEmpty()) continue;
            context.memory().remove(OPENED_KEY + position.get());
            return Optional.of(AiDecision.press(prompt, done.get(), "finish removing ships"));
        }
        return Optional.empty();
    }

    private static boolean removing(AiTurnContext context) {
        return REMOVE_TYPE.equalsIgnoreCase(context.game().getStoredValue(context.faction() + LATEST_ASSIGN_HITS));
    }

    private static boolean removesOneShip(Player seat, PromptButton button) {
        if (!CombatRules.isSingleUnitLoss(button)) return false;
        String[] parts = button.handlerId().split("_");
        if (!ASSIGN_HITS.equals(parts[0]) || !SINGLE_UNIT.equals(parts[2])) return false;
        UnitModel model = seat.getUnitFromAsyncID(parts[3]);
        return model != null && model.getIsShip() && model.getUnitType() != UnitType.Fighter;
    }

    private static boolean keepsCargoAboard(Game game, Player seat, PromptButton button) {
        int capacity = seat.getUnitFromAsyncID(button.handlerId().split("_")[3]).getCapacityValue();
        if (capacity <= 0) return true;
        Tile tile = game.getTileByPosition(positionOf(button));
        return tile != null && ButtonHelper.fleetCheck(seat, game, tile).capacitySlack() >= capacity;
    }

    private static String positionOf(PromptButton button) {
        return button.handlerId().split("_")[1];
    }

    private static boolean overSupplied(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && ButtonHelper.fleetCheck(seat, game, tile).fleetSupplyViolated();
    }
}
