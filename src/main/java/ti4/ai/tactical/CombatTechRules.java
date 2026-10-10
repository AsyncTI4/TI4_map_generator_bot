package ti4.ai.tactical;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class CombatTechRules {

    private static final String DIMENSIONAL_SPLICER = "ds";
    private static final String SPLICER_PREFIX = "assCannonNDihmohn_ds_";
    private static final String EXOTRIREME = "exo2";
    private static final String EXOTRIREME_PREFIX = "assCannonNDihmohn_exo_";
    private static final double EXOTRIREME_TRADE = 2.0;
    private static final String IMPULSE_START = "startImpulseCore_";
    private static final String IMPULSE_RESOLVE = "resolveImpulseCore_";
    private static final String IMPULSE_CORE_TEXT = "_Impulse Core_";
    private static final String SUPERCHARGE = "sc";
    private static final String SUPERCHARGE_BUTTON = "applytempcombatmod__tech__sc";
    private static final String SHIP_HIT_PREFIX = "hitOpponent_";
    private static final String SUSTAIN_LABEL = "Sustain";
    private static final double SUSTAINED_HIT_VALUE = 1.0;
    private static final String DONE_REMOVING = "deleteButtons";
    private static final List<UnitType> SACRIFICES = List.of(UnitType.Destroyer, UnitType.Cruiser);

    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> prompts) {
        return hitOpposingShip(context, prompts)
                .or(() -> takeImpulseCoreHit(context, prompts))
                .or(() -> dimensionalSplicer(context, prompts))
                .or(() -> impulseCore(context, prompts))
                .or(() -> supercharge(context, prompts))
                .or(() -> exotrireme(context, prompts));
    }

    private static Optional<AiDecision> hitOpposingShip(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> target = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(SHIP_HIT_PREFIX))
                    .max(Comparator.comparingDouble(button -> shipHitValue(context.game(), button)));
            if (target.isPresent()) {
                return Optional.of(AiDecision.press(prompt, target.get(), "hit the most valuable enemy ship"));
            }
        }
        return Optional.empty();
    }

    private static double shipHitValue(Game game, PromptButton button) {
        if (button.label().startsWith(SUSTAIN_LABEL)) return SUSTAINED_HIT_VALUE;
        String[] parts = button.handlerId().split("_");
        if (parts.length < 4) return 0;
        String unit = parts[2].replace("damaged", "").replace("galvanized", "");
        Player owner = game.getPlayerFromColorOrFaction(parts[3]);
        Optional<UnitType> type = Arrays.stream(UnitType.values())
                .filter(candidate -> candidate.plainName().equals(unit))
                .findFirst();
        if (owner == null || type.isEmpty()) return 0;
        return cost(owner, type.get());
    }

    private static Optional<AiDecision> takeImpulseCoreHit(AiTurnContext context, List<AiPrompt> prompts) {
        Player seat = context.seat();
        for (AiPrompt prompt : prompts) {
            if (!prompt.content().contains(IMPULSE_CORE_TEXT)) continue;
            List<PromptButton> picks = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && (CombatRules.isSingleUnitLoss(button) || CombatRules.isSingleUnitSustain(button)))
                    .toList();
            if (picks.isEmpty()) continue;
            String key = "impulseCoreHit|" + prompt.messageId();
            if (context.memory().has(key)) {
                Optional<PromptButton> done =
                        prompt.firstEnabled(button -> button.isUnowned() && DONE_REMOVING.equals(button.handlerId()));
                if (done.isEmpty()) continue;
                return Optional.of(AiDecision.press(prompt, done.get(), "finish the Impulse Core hit"));
            }
            List<PromptButton> nonFighters =
                    picks.stream().filter(button -> !isFighter(button)).toList();
            List<PromptButton> candidates = nonFighters.isEmpty() ? picks : nonFighters;
            PromptButton choice = candidates.stream()
                    .filter(CombatRules::isSingleUnitSustain)
                    .findFirst()
                    .or(() -> candidates.stream()
                            .filter(CombatRules::isSingleUnitLoss)
                            .min(Comparator.comparingDouble(button -> CombatRules.lossCost(seat, button))))
                    .orElseThrow();
            context.memory().put(key, "assigned");
            return Optional.of(AiDecision.press(prompt, choice, "take the Impulse Core hit"));
        }
        return Optional.empty();
    }

    private static boolean isFighter(PromptButton button) {
        String[] parts = button.handlerId().split("_");
        return parts.length > 3 && UnitType.Fighter.getValue().equals(parts[3]);
    }

    private static Optional<AiDecision> dimensionalSplicer(AiTurnContext context, List<AiPrompt> prompts) {
        Player seat = context.seat();
        if (!seat.hasTech(DIMENSIONAL_SPLICER)) return Optional.empty();
        return firstStartOfCombatButton(context, prompts, SPLICER_PREFIX, true)
                .filter(found -> BoardView.hasOwnShips(seat, found.tile())
                        && BoardView.hasEnemyShips(context.game(), seat, found.tile()))
                .map(found -> found.press(context, "hit an enemy ship with Dimensional Splicer"));
    }

    private static Optional<AiDecision> impulseCore(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> sacrifice = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(IMPULSE_RESOLVE))
                    .min(Comparator.comparingInt(CombatTechRules::sacrificeOrder));
            if (sacrifice.isPresent()) {
                return Optional.of(AiDecision.press(prompt, sacrifice.get(), "destroy a ship for Impulse Core"));
            }
        }
        return firstStartOfCombatButton(context, prompts, IMPULSE_START, false)
                .filter(found -> impulseCoreTrade(context.game(), context.seat(), found.tile()))
                .map(found -> found.press(context, "use Impulse Core"));
    }

    private static int sacrificeOrder(PromptButton button) {
        String type = StringUtils.substringAfterLast(button.handlerId(), "_");
        for (int i = 0; i < SACRIFICES.size(); i++) {
            if (SACRIFICES.get(i).getValue().equals(type)) return i;
        }
        return SACRIFICES.size();
    }

    private static boolean impulseCoreTrade(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        double sacrifice = SACRIFICES.stream()
                .filter(type -> BoardView.count(space, seat, type) > 0)
                .mapToDouble(type -> cost(seat, type))
                .min()
                .orElse(Double.MAX_VALUE);
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            Map<UnitType, Integer> ships = BoardView.ships(space, other);
            ships.remove(UnitType.Fighter);
            if (ships.isEmpty()) continue;
            boolean canSustain = ships.keySet().stream()
                    .anyMatch(type -> BoardView.model(other, type)
                                    .map(UnitModel::getSustainDamage)
                                    .orElse(false)
                            && BoardView.undamaged(space, other, type) > 0);
            double cheapest = ships.keySet().stream()
                    .mapToDouble(type -> cost(other, type))
                    .min()
                    .orElse(0);
            return !canSustain && cheapest >= sacrifice;
        }
        return false;
    }

    private static Optional<AiDecision> supercharge(AiTurnContext context, List<AiPrompt> prompts) {
        if (!context.seat().hasTechReady(SUPERCHARGE)) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> button = prompt.firstEnabled(candidate ->
                    candidate.isOwnedBy(context.faction()) && SUPERCHARGE_BUTTON.equals(candidate.handlerId()));
            String key = "supercharge|" + prompt.messageId();
            if (button.isEmpty() || context.memory().has(key)) continue;
            context.memory().put(key, "pressed");
            return Optional.of(AiDecision.press(prompt, button.get(), "exhaust Supercharge for this round"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> exotrireme(AiTurnContext context, List<AiPrompt> prompts) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasTech(EXOTRIREME)) return Optional.empty();
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> candidate.isUnowned() && candidate.handlerId().startsWith(EXOTRIREME_PREFIX));
            if (button.isEmpty()) continue;
            String position = StringUtils.removeStart(button.get().handlerId(), EXOTRIREME_PREFIX);
            Tile tile = game.getTileByPosition(position);
            if (tile == null || BoardView.count(BoardView.space(tile), seat, UnitType.Dreadnought) == 0) continue;
            int rounds = CombatRules.tracker(context, context.faction(), position, BoardView.SPACE);
            String key = "exotrireme|" + position + "|" + rounds;
            if (rounds == 0 || context.memory().has(key) || !roundFinished(game, seat, position, rounds)) continue;
            if (bestTwoEnemyShips(game, seat, tile) < EXOTRIREME_TRADE * cost(seat, UnitType.Dreadnought)) continue;
            context.memory().put(key, "pressed");
            return Optional.of(
                    AiDecision.press(prompt, button.get(), "trade a dreadnought for two ships with Exotrireme II"));
        }
        return Optional.empty();
    }

    private static boolean roundFinished(Game game, Player seat, String position, int rounds) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .allMatch(other -> CombatRules.tracker(game, other.getFaction(), position, BoardView.SPACE) >= rounds);
    }

    private static double bestTwoEnemyShips(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .flatMap(other -> BoardView.ships(space, other).entrySet().stream()
                        .flatMap(entry -> Stream.generate(() -> cost(other, entry.getKey()))
                                .limit(entry.getValue())))
                .sorted(Comparator.reverseOrder())
                .limit(2)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static double cost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }

    private record StartButton(AiPrompt prompt, PromptButton button, Tile tile, String key) {
        AiDecision press(AiTurnContext context, String reason) {
            context.memory().put(key, "pressed");
            return AiDecision.press(prompt, button, reason);
        }
    }

    private static Optional<StartButton> firstStartOfCombatButton(
            AiTurnContext context, List<AiPrompt> prompts, String prefix, boolean unowned) {
        for (AiPrompt prompt : prompts) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> (unowned ? candidate.isUnowned() : candidate.isOwnedBy(context.faction()))
                            && candidate.handlerId().startsWith(prefix));
            if (button.isEmpty()) continue;
            String position = StringUtils.removeStart(button.get().handlerId(), prefix);
            Tile tile = context.game().getTileByPosition(position);
            String key = prefix + "|" + prompt.messageId();
            if (tile == null
                    || context.memory().has(key)
                    || CombatRules.tracker(context, context.faction(), position, BoardView.SPACE) > 0) {
                continue;
            }
            return Optional.of(new StartButton(prompt, button.get(), tile, key));
        }
        return Optional.empty();
    }
}
