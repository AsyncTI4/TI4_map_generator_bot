package ti4.ai.sol;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.DefenseOutlook;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class BellumGloriosumRules {

    public static final String OFFER_PREFIX = "solBtBuild_";
    static final String SOURCE = ProductionPlanner.BELLUM_SOURCE;
    private static final String BREAKTHROUGH = "solbt";
    private static final String DECLINE = "deleteButtons";
    private static final String DONE_PRODUCING = "Done Producing Units";
    private static final String DONE_EXHAUSTING = "Done Exhausting Planets";
    private static final String PLACE_PREFIX = "place_";
    private static final String PAY_PREFIX = "deleteButtons_";
    private static final String ACTIVE_KEY = "bellumActive|";
    private static final String PLAN_KEY = "bellumPlan|";
    private static final String HANDLED_KEY = "bellumHandled|";
    private static final String FIELD = "~";
    private static final String BUILDING = "building";
    private static final String PAYING = "paying";
    private static final String DONE = "done";
    private static final long OFFER_WINDOW_MILLIS = 10 * 60_000L;
    private static final long MAIN_BUILD_WINDOW_MILLIS = 2 * 60_000L;
    private static final long STEP_WAIT_MILLIS = 20_000L;
    private static final long POLL_MILLIS = 3_000L;

    record Active(String stage, long since, String position) {

        String encode() {
            return String.join(FIELD, stage, String.valueOf(since), position);
        }

        Active at(String next, long now) {
            return new Active(next, now, position);
        }

        static Optional<Active> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 3 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            return Optional.of(new Active(fields[0], Long.parseLong(fields[1]), fields[2]));
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasUnlockedBreakthrough(BREAKTHROUGH)) return Optional.empty();
        Optional<Active> active = context.memory().get(ACTIVE_KEY).flatMap(Active::decode);
        if (active.isPresent() && !DONE.equals(active.get().stage())) return proceed(context, active.get());
        return offer(context);
    }

    private static Optional<AiDecision> offer(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        for (AiPrompt prompt : prompts) {
            if (context.now() - prompt.createdAtMillis() > OFFER_WINDOW_MILLIS || !Prompts.mentions(prompt, seat)) {
                continue;
            }
            Optional<PromptButton> build = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(OFFER_PREFIX));
            if (build.isEmpty() || context.memory().has(HANDLED_KEY + prompt.messageId())) continue;
            if (mainBuildOpen(context, prompt)) return Optional.empty();
            String[] parts = build.get().handlerId().split("_");
            Tile tile = parts.length == 3 ? game.getTileByPosition(parts[1]) : null;
            if (tile == null || !StringUtils.isNumeric(parts[2])) continue;
            context.memory().put(HANDLED_KEY + prompt.messageId(), "seen");
            BuildPlan plan = plan(game, seat, tile, Integer.parseInt(parts[2]));
            if (!plan.isEmpty()) {
                context.memory().put(PLAN_KEY, plan.encode());
                remember(context, new Active(BUILDING, context.now(), tile.getPosition()));
                return Optional.of(AiDecision.press(
                        prompt,
                        build.get(),
                        "produce up to " + parts[2] + " more ground forces and fighters, paid for"));
            }
            return prompt.firstEnabled(button -> button.isUnowned() && DECLINE.equals(button.handlerId()))
                    .map(decline ->
                            AiDecision.press(prompt, decline, "decline Bellum Gloriosum, nothing worth building"));
        }
        return Optional.empty();
    }

    static BuildPlan plan(Game game, Player seat, Tile tile, int limit) {
        DefenseOutlook defense = new DefenseOutlook(game, seat);
        String planet =
                GroundDrops.bestPlanetIn(game, seat, tile, Optional.empty()).orElse(null);
        return ProductionPlanner.bellumGloriosum(game, seat, tile, limit, planet, defense);
    }

    private static Optional<AiDecision> proceed(AiTurnContext context, Active active) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        Optional<AiDecision> pay = TacticalRules.payForUnits(context, prompts, SOURCE);
        if (pay.isPresent()) {
            if (PAYING.equals(active.stage()) && finishesPaying(pay.get())) {
                remember(context, active.at(DONE, context.now()));
            }
            return pay;
        }
        Optional<BuildPlan> plan = context.memory().get(PLAN_KEY).flatMap(BuildPlan::decode);
        Optional<AiDecision> place = TacticalRules.placeUnits(context, prompts, SOURCE, active.position(), plan);
        if (place.isPresent()) {
            if (finishesBuilding(place.get())) remember(context, active.at(PAYING, context.now()));
            return place;
        }
        long until = active.since() + STEP_WAIT_MILLIS;
        if (context.now() >= until) {
            remember(context, active.at(DONE, context.now()));
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(Math.min(until, context.now() + POLL_MILLIS), "Bellum Gloriosum"));
    }

    private static boolean finishesBuilding(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                && DONE_PRODUCING.equals(press.button().label());
    }

    private static boolean finishesPaying(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                && (PAY_PREFIX + SOURCE).equals(press.button().handlerId())
                && DONE_EXHAUSTING.equals(press.button().label());
    }

    private static boolean mainBuildOpen(AiTurnContext context, AiPrompt offer) {
        long since = Math.min(offer.createdAtMillis() - MAIN_BUILD_WINDOW_MILLIS, Prompts.turnStart(context));
        return context.prompts().stream()
                .filter(prompt -> prompt != offer && prompt.createdAtMillis() >= since)
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .anyMatch(button -> opensMainBuild(context, button));
    }

    private static boolean opensMainBuild(AiTurnContext context, PromptButton button) {
        if (button.handlerId().contains(SOURCE)) return false;
        if (button.isOwnedBy(context.faction())) {
            return button.handlerId().startsWith(PLACE_PREFIX) || DONE_PRODUCING.equals(button.label());
        }
        return button.isUnowned()
                && button.handlerId().startsWith(PAY_PREFIX)
                && DONE_EXHAUSTING.equals(button.label());
    }

    private static void remember(AiTurnContext context, Active active) {
        context.memory().put(ACTIVE_KEY, active.encode());
    }
}
