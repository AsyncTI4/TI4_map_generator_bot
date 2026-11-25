package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.AnomalyRules;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Departure;
import ti4.ai.eval.MoveBudget;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;

@UtilityClass
class RiftRolls {

    static final String QUEUE_KEY = "riftRolls|";
    static final String OPENED_KEY = "riftRollsOpened|";
    static final String RETREAT_KEY = "retreatRift|";
    private static final String OPEN_PREFIX = "getRiftButtons_";
    private static final String ROLL_PREFIX = "riftUnit_";
    private static final String ROLL_ALL_PREFIX = "riftAllUnits_";
    private static final String DONE = "doneRifting";
    private static final String DAMAGED = "damaged";
    private static final String SEPARATOR = ",";
    private static final String DONE_STATE = "done";
    private static final String OPENED_AT = "|openedAt";
    private static final String SEEN_AT = "|seenAt";
    private static final String PRESSES_KEY = "riftPresses|";
    private static final long RETREAT_RIFT_BUTTON_WAIT_MILLIS = 15_000L;
    private static final long ROLL_BUTTONS_WAIT_MILLIS = 60_000L;
    private static final long ROLL_BUTTONS_RECHECK_MILLIS = 5_000L;

    static void record(AiTurnContext context, String target) {
        Game game = context.game();
        Player seat = context.seat();
        Tile tile = game.getTileByPosition(target);
        if (tile == null) return;
        Map<String, Map<UnitType, Integer>> damaged = new LinkedHashMap<>();
        List<Departure> moved = movedThisAction(game, seat, damaged);
        List<Departure> exits = MoveBudget.riftExits(game, seat, tile, moved);
        List<String> tokens = new ArrayList<>();
        for (Departure exit : exits) {
            Map<UnitType, Integer> damagedHere = damaged.getOrDefault(exit.origin(), new EnumMap<>(UnitType.class));
            for (int each = 0; each < exit.count(); each++) {
                int damagedLeft = damagedHere.getOrDefault(exit.type(), 0);
                String name = exit.type().plainName();
                if (damagedLeft > 0) {
                    damagedHere.put(exit.type(), damagedLeft - 1);
                    tokens.add(name + DAMAGED);
                } else {
                    tokens.add(name);
                }
            }
        }
        tokens.sort(Comparator.comparing((String token) -> !token.endsWith(DAMAGED))
                .thenComparing(Comparator.naturalOrder()));
        String key = QUEUE_KEY + TacticalRules.actionKey(context);
        if (tokens.isEmpty()) {
            context.memory().remove(key);
            return;
        }
        context.memory().put(key, String.join(SEPARATOR, tokens));
        context.memory().remove(OPENED_KEY + TacticalRules.actionKey(context));
    }

    private static List<Departure> movedThisAction(
            Game game, Player seat, Map<String, Map<UnitType, Integer>> damaged) {
        Map<String, Map<UnitType, Integer>> counts = new LinkedHashMap<>();
        game.getTacticalActionDisplacement().forEach((key, units) -> {
            String origin = StringUtils.substringBefore(key, "-");
            units.forEach((unit, states) -> {
                if (!seat.getColor().equals(unit.getColor())) return;
                int total = 0;
                for (int index = 0; index < states.size(); index++) {
                    int count = states.get(index) == null ? 0 : states.get(index);
                    total += count;
                    if (isDamaged(index) && count > 0) {
                        damaged.computeIfAbsent(origin, ignored -> new EnumMap<>(UnitType.class))
                                .merge(unit.unitType(), count, Integer::sum);
                    }
                }
                if (total > 0) {
                    counts.computeIfAbsent(origin, ignored -> new EnumMap<>(UnitType.class))
                            .merge(unit.unitType(), total, Integer::sum);
                }
            });
        });
        List<Departure> moved = new ArrayList<>();
        counts.forEach(
                (origin, types) -> types.forEach((type, count) -> moved.add(new Departure(origin, type, count))));
        return moved;
    }

    private static boolean isDamaged(int index) {
        return index < UnitState.values().length && (index & UnitState.DMG) != 0;
    }

    // TODO: a lost rift carrier removes the cheapest excess cargo, not the cargo it carried (LRR 41.2b)
    static Optional<AiDecision> next(AiTurnContext context, List<AiPrompt> turn, Tile tile) {
        String actionKey = TacticalRules.actionKey(context);
        String key = QUEUE_KEY + actionKey;
        Optional<String> stored = context.memory().get(key);
        if (stored.isEmpty()) return Optional.empty();
        String position = tile.getPosition();
        List<String> queue = decode(stored.get());
        Optional<AiPrompt> rolling = rollPrompt(context, turn, position);
        if (rolling.isPresent()) {
            Optional<AiDecision> roll = rollNext(context, rolling.get(), position, queue, key);
            if (roll.isPresent()) return roll;
            return finish(context, rolling.get(), position, () -> {
                context.memory().remove(key);
                context.memory().remove(OPENED_KEY + actionKey);
                forgetPresses(context, key);
            });
        }
        if (queue.isEmpty()) {
            context.memory().remove(key);
            return Optional.empty();
        }
        Optional<String> opened = context.memory().get(OPENED_KEY + actionKey);
        if (opened.isPresent()) {
            return awaitRollButtons(context, opened.get(), () -> {
                context.memory().remove(key);
                context.memory().remove(OPENED_KEY + actionKey);
            });
        }
        Optional<Match> open = Prompts.owned(turn, context.faction(), (OPEN_PREFIX + position)::equals);
        if (open.isPresent()) {
            context.memory().put(OPENED_KEY + actionKey, String.valueOf(context.now()));
            return Optional.of(open.get().press("roll for the ships that left a gravity rift"));
        }
        if (turn.isEmpty()) return Optional.empty();
        context.memory().remove(key);
        return Optional.of(new AiDecision.Unsure(
                turn.getFirst(), "planned gravity-rift rolls but the engine offered no rift button"));
    }

    static Optional<AiDecision> beforeRetreat(AiTurnContext context, List<AiPrompt> prompts, String position) {
        Game game = context.game();
        Player seat = context.seat();
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !AnomalyRules.rollsOnRetreat(game, seat, tile)) return Optional.empty();
        String key = RETREAT_KEY + game.getRound() + "|" + position;
        Optional<String> state = context.memory().get(key);
        if (state.filter(DONE_STATE::equals).isPresent()) return Optional.empty();
        Optional<AiPrompt> rolling = rollPrompt(context, prompts, position);
        if (rolling.isPresent() && state.isPresent()) {
            List<String> queue = decode(state.get());
            Optional<AiDecision> roll = rollNext(context, rolling.get(), position, queue, key);
            if (roll.isPresent()) return roll;
            return finish(context, rolling.get(), position, () -> {
                context.memory().put(key, DONE_STATE);
                forgetPresses(context, key);
            });
        }
        if (state.isPresent()) {
            return awaitRollButtons(
                    context,
                    context.memory().get(key + OPENED_AT).orElse("0"),
                    () -> context.memory().put(key, DONE_STATE));
        }
        Optional<Match> open = Prompts.owned(prompts, context.faction(), (OPEN_PREFIX + position)::equals);
        if (open.isEmpty()) return awaitRetreatRiftButton(context, key);
        List<String> tokens = retreatingShips(seat, tile);
        if (tokens.isEmpty()) return Optional.empty();
        context.memory().put(key, String.join(SEPARATOR, tokens));
        context.memory().put(key + OPENED_AT, String.valueOf(context.now()));
        return Optional.of(open.get().press("roll for the ships retreating out of a gravity rift"));
    }

    private static Optional<AiDecision> awaitRetreatRiftButton(AiTurnContext context, String key) {
        String seenKey = key + SEEN_AT;
        Optional<String> seen = context.memory().get(seenKey);
        if (seen.isEmpty()) {
            context.memory().put(seenKey, String.valueOf(context.now()));
            return Optional.of(new AiDecision.Wait(
                    context.now() + ROLL_BUTTONS_RECHECK_MILLIS, "the gravity-rift button for the retreat"));
        }
        long since = StringUtils.isNumeric(seen.get()) ? Long.parseLong(seen.get()) : 0;
        if (context.now() - since < RETREAT_RIFT_BUTTON_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(
                    context.now() + ROLL_BUTTONS_RECHECK_MILLIS, "the gravity-rift button for the retreat"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> awaitRollButtons(AiTurnContext context, String openedAt, Runnable giveUp) {
        long since = StringUtils.isNumeric(openedAt) ? Long.parseLong(openedAt) : 0;
        if (context.now() - since < ROLL_BUTTONS_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(
                    context.now() + ROLL_BUTTONS_RECHECK_MILLIS, "the gravity-rift roll buttons to appear"));
        }
        giveUp.run();
        return Optional.empty();
    }

    private static List<String> retreatingShips(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        List<String> damagedFirst = new ArrayList<>();
        List<String> intact = new ArrayList<>();
        int capacity = 0;
        for (Map.Entry<UnitType, Integer> ship : BoardView.ships(space, seat).entrySet()) {
            UnitType type = ship.getKey();
            if (type == UnitType.Fighter) continue;
            capacity += ship.getValue() * BoardView.capacity(seat, type);
            addTokens(damagedFirst, intact, type, ship.getValue(), BoardView.damaged(space, seat, type));
        }
        if (seat.hasFF2Tech()) {
            int room = Math.max(0, capacity - BoardView.groundForces(space, seat));
            int flying = Math.max(0, BoardView.count(space, seat, UnitType.Fighter) - room);
            addTokens(damagedFirst, intact, UnitType.Fighter, flying, 0);
        }
        damagedFirst.addAll(intact);
        return damagedFirst;
    }

    private static void addTokens(List<String> damaged, List<String> intact, UnitType type, int count, int hurt) {
        int damagedCount = Math.min(count, hurt);
        for (int each = 0; each < damagedCount; each++) damaged.add(type.plainName() + DAMAGED);
        for (int each = damagedCount; each < count; each++) intact.add(type.plainName());
    }

    private static Optional<AiPrompt> rollPrompt(AiTurnContext context, List<AiPrompt> prompts, String position) {
        return Prompts.newestWith(
                prompts,
                button -> button.isOwnedBy(context.faction())
                        && (button.handlerId().startsWith(ROLL_PREFIX + position + "_")
                                || button.handlerId().equals(ROLL_ALL_PREFIX + position)));
    }

    private record Roll(PromptButton button, int ships, String handlerId) {}

    private static Optional<AiDecision> rollNext(
            AiTurnContext context, AiPrompt prompt, String position, List<String> queue, String key) {
        while (!queue.isEmpty()) {
            String token = queue.getFirst();
            Optional<Roll> roll = rollButton(context, prompt, position, token, copiesAhead(queue, token), key);
            if (roll.isEmpty()) {
                queue.removeFirst();
                continue;
            }
            for (int each = 0; each < roll.get().ships(); each++) queue.removeFirst();
            context.memory().put(key, String.join(SEPARATOR, queue));
            String pressed = PRESSES_KEY + key + "|" + roll.get().handlerId();
            context.memory().put(pressed, String.valueOf(presses(context, pressed) + 1));
            String what = roll.get().ships() == 1 ? "a " + token : roll.get().ships() + " " + token;
            return Optional.of(AiDecision.press(prompt, roll.get().button(), "roll for " + what + " leaving the rift"));
        }
        context.memory().put(key, "");
        return Optional.empty();
    }

    private static Optional<Roll> rollButton(
            AiTurnContext context, AiPrompt prompt, String position, String token, int copies, String key) {
        List<Integer> sizes = copies >= 2 ? List.of(2, 1) : List.of(1);
        for (boolean withinRepeatLimit : List.of(true, false)) {
            for (int ships : sizes) {
                String wanted = ROLL_PREFIX + position + "_" + ships + token;
                if (withinRepeatLimit
                        && presses(context, PRESSES_KEY + key + "|" + wanted) >= AiSettings.MAX_ATTEMPTS_PER_PROMPT) {
                    continue;
                }
                Optional<PromptButton> button = prompt.firstEnabled(found ->
                        found.isOwnedBy(context.faction()) && found.handlerId().equalsIgnoreCase(wanted));
                if (button.isPresent()) return Optional.of(new Roll(button.get(), ships, wanted));
            }
        }
        return Optional.empty();
    }

    private static int copiesAhead(List<String> queue, String token) {
        int copies = 0;
        for (String queued : queue) {
            if (!queued.equals(token)) break;
            copies++;
        }
        return copies;
    }

    private static int presses(AiTurnContext context, String key) {
        String stored = context.memory().get(key).orElse("0");
        return StringUtils.isNumeric(stored) ? Integer.parseInt(stored) : 0;
    }

    private static void forgetPresses(AiTurnContext context, String key) {
        context.memory().keysStartingWith(PRESSES_KEY + key + "|").forEach(context.memory()::remove);
    }

    private static Optional<AiDecision> finish(
            AiTurnContext context, AiPrompt prompt, String position, Runnable settle) {
        if (FleetSupplyRules.overLimits(context.game(), context.seat(), position)) return Optional.empty();
        Optional<PromptButton> done =
                prompt.firstEnabled(button -> button.isUnowned() && DONE.equals(button.handlerId()));
        if (done.isEmpty()) return Optional.empty();
        settle.run();
        return Optional.of(AiDecision.press(prompt, done.get(), "finish the gravity-rift rolls"));
    }

    private static List<String> decode(String stored) {
        List<String> queue = new ArrayList<>();
        for (String token : StringUtils.split(stored, SEPARATOR)) {
            if (!token.isBlank()) queue.add(token);
        }
        return queue;
    }
}
