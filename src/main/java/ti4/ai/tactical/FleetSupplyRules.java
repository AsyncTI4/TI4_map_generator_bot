package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import ti4.helpers.ButtonHelper.FleetCheck;
import ti4.helpers.Units.UnitState;
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
    private static final int SPACE_UNIT_PARTS = 5;
    private static final int FIGHTER_II_OVERFLOW_INDEX = 4;
    private static final Set<UnitType> CARGO = Set.of(UnitType.Fighter, UnitType.Infantry, UnitType.Mech);

    public static Optional<AiDecision> removeExcessShips(AiTurnContext context) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        Optional<AiDecision> remove = removeCheapestUnit(context, prompts);
        if (remove.isPresent()) return remove;
        Optional<AiDecision> open = openRemoval(context, prompts);
        if (open.isPresent()) return open;
        return finishRemoval(context, prompts);
    }

    private static Optional<AiDecision> removeCheapestUnit(AiTurnContext context, List<AiPrompt> prompts) {
        if (!removing(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> cheapest = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && removesOneUnit(seat, button))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .filter(button -> helpsFit(game, seat, button))
                    .min(Comparator.comparingDouble((PromptButton button) -> CombatRules.lossCost(seat, button))
                            .thenComparingInt(button -> isFighter(seat, button) ? 0 : 1));
            if (cheapest.isPresent()) {
                return Optional.of(AiDecision.press(
                        prompt, cheapest.get(), "remove its least valuable unit to fit its fleet supply and capacity"));
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
            if (!overLimits(game, seat, position)) continue;
            context.memory().put(OPENED_KEY + position, String.valueOf(context.now()));
            return Optional.of(AiDecision.press(prompt, open.get(), "get back within its fleet supply and capacity"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> finishRemoval(AiTurnContext context, List<AiPrompt> prompts) {
        if (!removing(context)) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            Optional<String> position = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && removesOneUnit(seat, button))
                    .map(FleetSupplyRules::positionOf)
                    .filter(candidate -> context.memory().has(OPENED_KEY + candidate))
                    .findFirst();
            if (position.isEmpty() || overLimits(game, seat, position.get())) continue;
            Optional<PromptButton> done =
                    prompt.firstEnabled(button -> button.isUnowned() && DONE.equals(button.handlerId()));
            if (done.isEmpty()) continue;
            context.memory().remove(OPENED_KEY + position.get());
            return Optional.of(AiDecision.press(prompt, done.get(), "finish removing units"));
        }
        return Optional.empty();
    }

    private static boolean removing(AiTurnContext context) {
        return REMOVE_TYPE.equalsIgnoreCase(context.game().getStoredValue(context.faction() + LATEST_ASSIGN_HITS));
    }

    private static boolean removesOneUnit(Player seat, PromptButton button) {
        if (!CombatRules.isSingleUnitLoss(button)) return false;
        String[] parts = button.handlerId().split("_");
        return ASSIGN_HITS.equals(parts[0]) && SINGLE_UNIT.equals(parts[2]) && modelOf(seat, button) != null;
    }

    private static boolean helpsFit(Game game, Player seat, PromptButton button) {
        Tile tile = game.getTileByPosition(positionOf(button));
        UnitModel model = modelOf(seat, button);
        if (tile == null || model == null) return false;
        FleetCheck check = ButtonHelper.fleetCheck(seat, game, tile);
        if (CARGO.contains(model.getUnitType())) {
            if (!inSpace(button)) return false;
            return check.capacityViolated() || (check.fleetSupplyViolated() && fighterIIOverflow(check, model));
        }
        return model.getIsShip() && check.fleetSupplyViolated() && keepsCargoAboard(check, model);
    }

    private static boolean keepsCargoAboard(FleetCheck check, UnitModel ship) {
        int capacity = ship.getCapacityValue();
        return capacity <= 0 || check.capacitySlack() >= capacity;
    }

    private static boolean fighterIIOverflow(FleetCheck check, UnitModel model) {
        int[] counts = check.counts();
        return model.getUnitType() == UnitType.Fighter
                && counts.length > FIGHTER_II_OVERFLOW_INDEX
                && counts[FIGHTER_II_OVERFLOW_INDEX] > 0;
    }

    private static boolean inSpace(PromptButton button) {
        String[] parts = button.handlerId().split("_");
        if (parts.length == SPACE_UNIT_PARTS) return true;
        return parts.length == SPACE_UNIT_PARTS + 1 && isUnitState(parts[SPACE_UNIT_PARTS - 1]);
    }

    private static boolean isUnitState(String part) {
        for (UnitState state : UnitState.values()) {
            if (state.name().equals(part)) return true;
        }
        return false;
    }

    private static boolean isFighter(Player seat, PromptButton button) {
        UnitModel model = modelOf(seat, button);
        return model != null && model.getUnitType() == UnitType.Fighter;
    }

    private static UnitModel modelOf(Player seat, PromptButton button) {
        return seat.getUnitFromAsyncID(button.handlerId().split("_")[3]);
    }

    private static String positionOf(PromptButton button) {
        return button.handlerId().split("_")[1];
    }

    private static boolean overLimits(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        FleetCheck check = ButtonHelper.fleetCheck(seat, game, tile);
        return check.fleetSupplyViolated() || check.capacityViolated();
    }
}
