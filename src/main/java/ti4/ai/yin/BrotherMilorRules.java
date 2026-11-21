package ti4.ai.yin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatForecast;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class BrotherMilorRules {

    static final String AGENT = "yinagent";
    static final String OFFER_PREFIX = "yinagent_";
    static final String OWNER_PREFIX = "exhaustAgent_yinagent_";
    static final String PLACE_PREFIX = "placeOneNDone_skipbuild_";
    static final String FIGHTERS = "2ff_";
    static final String INFANTRY = "2gf_";
    static final String AGENT_NAME = "Brother Milor";
    static final int UNITS = 2;
    static final double MIN_GAIN = 1.0;
    private static final String BASELINE_KEY = "milorBaseline|";
    private static final String FLOW_KEY = "milor|";
    private static final String FIELD = "~";
    private static final String ITEM = ";";
    private static final String EQUALS = "=";
    private static final String OWNER = "owner";
    private static final String PLACE = "place";
    private static final long STEP_WAIT_MILLIS = 30_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    record Flow(String stage, String position, String holder, long since) {

        String encode() {
            return String.join(FIELD, stage, position, holder, String.valueOf(since));
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 4 || !StringUtils.isNumeric(fields[3])) return Optional.empty();
            return Optional.of(new Flow(fields[0], fields[1], fields[2], Long.parseLong(fields[3])));
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts());
        return continueFlow(context, visible)
                .or(() -> receive(context, visible))
                .or(() -> offer(context, visible));
    }

    private static Optional<AiDecision> offer(AiTurnContext context, List<AiPrompt> visible) {
        Player seat = context.seat();
        if (!seat.hasUnexhaustedLeader(AGENT) || context.memory().has(FLOW_KEY)) return Optional.empty();
        Game game = context.game();
        for (AiPrompt prompt : visible) {
            if (prompt.source() != PromptSource.COMBAT_THREAD) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(OFFER_PREFIX)) continue;
                String position = StringUtils.removeStart(button.handlerId(), OFFER_PREFIX);
                Tile tile = game.getTileByPosition(position);
                if (tile == null) continue;
                String baselineKey = BASELINE_KEY + prompt.messageId();
                Optional<String> baseline = context.memory().get(baselineKey);
                if (baseline.isEmpty()) {
                    context.memory().put(baselineKey, encode(counts(seat, tile)));
                    continue;
                }
                Optional<String> holder = worthUsing(game, seat, tile, decode(baseline.get()));
                if (holder.isEmpty()) continue;
                context.memory().put(FLOW_KEY, new Flow(OWNER, position, holder.get(), context.now()).encode());
                return Optional.of(AiDecision.press(prompt, button, "use Brother Milor to replace its losses"));
            }
        }
        return Optional.empty();
    }

    static Optional<String> worthUsing(Game game, Player seat, Tile tile, Map<String, Integer> baseline) {
        String best = null;
        double bestGain = MIN_GAIN;
        for (Map.Entry<String, Integer> entry : counts(seat, tile).entrySet()) {
            String holder = entry.getKey();
            int before = baseline.getOrDefault(holder, 0);
            if (entry.getValue() >= before || entry.getValue() == 0) continue;
            if (!opponentStillThere(game, seat, tile, holder)) continue;
            boolean space = BoardView.SPACE.equals(holder);
            UnitType type = space ? UnitType.Fighter : UnitType.Infantry;
            double gain = CombatForecast.reinforcementGain(game, seat, tile.getPosition(), holder, type, UNITS)
                    .gain();
            if (gain >= bestGain) {
                bestGain = gain;
                best = holder;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<AiDecision> continueFlow(AiTurnContext context, List<AiPrompt> visible) {
        Optional<Flow> flow = context.memory().get(FLOW_KEY).flatMap(Flow::decode);
        if (flow.isEmpty()) return Optional.empty();
        List<AiPrompt> fresh = visible.stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        String faction = context.faction();
        if (OWNER.equals(flow.get().stage())) {
            String own = OWNER_PREFIX + flow.get().position() + "_" + faction;
            Optional<Match> owner = Prompts.owned(fresh, faction, own::equals);
            if (owner.isPresent()) {
                context.memory()
                        .put(
                                FLOW_KEY,
                                new Flow(
                                                PLACE,
                                                flow.get().position(),
                                                flow.get().holder(),
                                                context.now())
                                        .encode());
                return Optional.of(owner.get().press("give Brother Milor's units to itself"));
            }
        } else {
            Optional<Match> place = Prompts.owned(fresh, faction, placeHandler(flow.get())::equals);
            if (place.isPresent()) {
                context.memory().remove(FLOW_KEY);
                return Optional.of(place.get().press("place Brother Milor's 2 units"));
            }
        }
        long until = flow.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Brother Milor"));
        context.memory().remove(FLOW_KEY);
        return Optional.empty();
    }

    private static String placeHandler(Flow flow) {
        return BoardView.SPACE.equals(flow.holder())
                ? PLACE_PREFIX + FIGHTERS + flow.position()
                : PLACE_PREFIX + INFANTRY + flow.holder();
    }

    private static Optional<AiDecision> receive(AiTurnContext context, List<AiPrompt> visible) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : visible) {
            if (!prompt.content().contains(AGENT_NAME)) continue;
            List<PromptButton> places = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(PLACE_PREFIX)
                            && !context.alreadyPressed(prompt, button))
                    .toList();
            if (places.isEmpty()) continue;
            PromptButton choice = places.stream()
                    .filter(button -> button.handlerId().startsWith(PLACE_PREFIX + INFANTRY))
                    .filter(button ->
                            contested(game, seat, StringUtils.removeStart(button.handlerId(), PLACE_PREFIX + INFANTRY)))
                    .findFirst()
                    .or(() -> places.stream()
                            .filter(button -> button.handlerId().startsWith(PLACE_PREFIX + FIGHTERS))
                            .findFirst())
                    .orElse(places.getFirst());
            return Optional.of(AiDecision.press(prompt, choice, "place the units Brother Milor gave it"));
        }
        return Optional.empty();
    }

    private static boolean contested(Game game, Player seat, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        return planet != null && BoardView.enemyGroundForcesOn(game, seat, planet);
    }

    private static boolean opponentStillThere(Game game, Player seat, Tile tile, String holder) {
        if (BoardView.SPACE.equals(holder)) return BoardView.hasEnemyShips(game, seat, tile);
        UnitHolder planet = tile.getUnitHolders().get(holder);
        return planet != null && BoardView.enemyGroundForcesOn(game, seat, planet);
    }

    static Map<String, Integer> counts(Player seat, Tile tile) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        UnitHolder space = BoardView.space(tile);
        counts.put(
                BoardView.SPACE,
                BoardView.ships(space, seat).values().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
        for (Planet planet : tile.getPlanetUnitHolders()) {
            counts.put(planet.getName(), BoardView.groundForces(planet, seat));
        }
        return counts;
    }

    static String encode(Map<String, Integer> counts) {
        List<String> items = new ArrayList<>();
        counts.forEach((holder, count) -> items.add(holder + EQUALS + count));
        return String.join(ITEM, items);
    }

    static Map<String, Integer> decode(String encoded) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String item : StringUtils.split(encoded, ITEM)) {
            String holder = StringUtils.substringBefore(item, EQUALS);
            String count = StringUtils.substringAfter(item, EQUALS);
            if (StringUtils.isNumeric(count)) counts.put(holder, Integer.parseInt(count));
        }
        return counts;
    }
}
