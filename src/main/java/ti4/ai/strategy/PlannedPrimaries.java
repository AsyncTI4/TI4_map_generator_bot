package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.agenda.AgendaPeek;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.DefenseOutlook;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

@UtilityClass
class PlannedPrimaries {

    static final String CONSTRUCTION_SOURCE = "construction";
    private static final String DIPLO_SYSTEM = "diploSystem";
    private static final String DIPLO_PREFIX = "diplo_";
    private static final String DIPLO_SUFFIX = "_diploP";
    private static final String DIPLOMACY_LOCK = "diplomacyLock";
    private static final String DIPLOMACY_LOCK_PICKED = "diplomacyLockPicked";
    private static final String DRAW_AGENDAS = "drawAgenda_2";
    private static final String AGENDA_PEEK = "agendaPeek";
    private static final String TOP_AGENDA = "topAgenda_";
    private static final String BOTTOM_AGENDA = "bottomAgenda_";
    private static final String CONSTRUCTION_PRODUCE = "constructionPrimary_produce";
    private static final String CONSTRUCTION_BUILD = "constructionBuild_";
    private static final String CONSTRUCTION_PRODUCE_STEP = "constructionProduce";
    private static final String CONSTRUCTION_PICK_STEP = "constructionBuildPick";
    private static final String CONSTRUCTION_BUILD_KEY = "constructionBuild|";
    private static final String DONE_PRODUCING_PREFIX = "deleteButtons_" + CONSTRUCTION_SOURCE + "_";
    private static final String STEP_TIME_KEY = "plannedPrimaryStep|";
    private static final String TARGET_KEY = "plannedPrimaryTarget|";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long PRODUCTION_WINDOW_MILLIS = 15 * 60_000L;
    private static final Set<CardUse> PRODUCTION_USES = Set.of(CardUse.CONSTRUCTION_PRODUCE, CardUse.OPENING_EXPANSION);

    static Optional<TacticalPlan> warfareTarget(AiTurnContext context, int initiative) {
        Game game = context.game();
        Player seat = context.seat();
        Optional<TacticalPlan> best = TacticalPlanner.bestForWarfare(game, seat);
        Optional<TacticalPlan> planned = CardPlanMemory.read(context, initiative)
                .flatMap(CardPlan::main)
                .filter(Benefit::hasTarget)
                .flatMap(benefit -> TacticalPlanner.forWarfareTarget(game, seat, benefit.target()))
                .filter(plan -> plan.score() >= TacticalPlanner.MIN_SCORE && activatableByWarfare(game, seat, plan));
        if (planned.isEmpty()) return best;
        double bestScore = best.map(TacticalPlan::score).orElse(0.0);
        if (planned.get().score() < bestScore - CardValueScale.REPLAN_DELTA) return best;
        return planned;
    }

    private static boolean activatableByWarfare(Game game, Player seat, TacticalPlan plan) {
        Tile tile = game.getTileByPosition(plan.target());
        return tile != null && (tile.hasPlayerCC(seat) || ButtonHelper.canActivateTile(game, seat, tile));
    }

    static Optional<String> tokenToRemove(Game game, Player seat, List<String> offered) {
        return WarfareUses.bestTokenToRemove(PlanInputs.of(game, seat, PlanMoment.ACTION), offered);
    }

    static Optional<AiDecision> lockWithDiplomacy(AiTurnContext context, AiPrompt card, int initiative) {
        if (CardMessages.done(context, DIPLOMACY_LOCK_PICKED)) return Optional.empty();
        if (CardVariant.of(context.game(), initiative) != CardVariant.POK)
            return finish(context, DIPLOMACY_LOCK_PICKED);
        if (!CardMessages.done(context, DIPLOMACY_LOCK)) {
            CardMessages.markDone(context, DIPLOMACY_LOCK);
            Optional<String> target = lockTarget(context, initiative);
            if (target.isEmpty()) return finish(context, DIPLOMACY_LOCK_PICKED);
            Optional<AiDecision> press = CardMessages.pressOnce(
                    context, card, DIPLO_SYSTEM, "lock " + target.get() + " with Diplomacy against attacks");
            if (press.isEmpty()) return finish(context, DIPLOMACY_LOCK_PICKED);
            remember(context, target.get());
            return press;
        }
        String target = context.memory().get(TARGET_KEY + context.turnKey()).orElse("");
        Optional<Match> pick = Prompts.owned(
                Prompts.thisTurn(context),
                context.faction(),
                handler -> handler.startsWith(DIPLO_PREFIX)
                        && handler.endsWith(DIPLO_SUFFIX)
                        && inTile(
                                context.game(),
                                StringUtils.substringBetween(handler, DIPLO_PREFIX, DIPLO_SUFFIX),
                                target));
        if (pick.isPresent()) {
            CardMessages.markDone(context, DIPLOMACY_LOCK_PICKED);
            return Optional.of(pick.get().press("choose " + target + " for Diplomacy"));
        }
        return waitOrFinish(context, DIPLOMACY_LOCK_PICKED, "the Diplomacy system choice");
    }

    private static Optional<String> lockTarget(AiTurnContext context, int initiative) {
        Game game = context.game();
        Player seat = context.seat();
        PlanInputs in = PlanInputs.of(game, seat, PlanMoment.ACTION);
        Optional<String> planned = CardPlanMemory.read(context, initiative)
                .flatMap(plan -> plan.benefit(CardUse.DIPLOMACY_PROTECT))
                .map(Benefit::target)
                .filter(position -> DiplomacyUses.isLockable(game, seat, position))
                .filter(position -> DiplomacyUses.protections(in).stream()
                        .anyMatch(protection -> protection.target().equals(position)));
        return planned.or(() -> DiplomacyUses.bestProtection(in).map(Benefit::target))
                .or(() -> DiplomacyUses.bestLockTarget(in));
    }

    private static boolean inTile(Game game, String planet, String position) {
        if (StringUtils.isBlank(planet)) return false;
        Tile tile = game.getTileFromPlanet(planet);
        return tile != null && tile.getPosition().equals(position);
    }

    static Optional<AiDecision> peekAgendas(AiTurnContext context) {
        Game game = context.game();
        if (!game.isCustodiansScored() || game.isTwilightsFallMode()) return Optional.empty();
        if (!CardMessages.done(context, AGENDA_PEEK)) {
            Optional<Match> draw = Prompts.owned(Prompts.thisTurn(context), context.faction(), DRAW_AGENDAS::equals);
            CardMessages.markDone(context, AGENDA_PEEK);
            if (draw.isEmpty()
                    || context.alreadyPressed(draw.get().prompt(), draw.get().button())) {
                return Optional.empty();
            }
            return Optional.of(draw.get().press("look at the top two agendas with Politics"));
        }
        return placeAgenda(context);
    }

    private static Optional<AiDecision> placeAgenda(AiTurnContext context) {
        Game game = context.game();
        Map<Integer, String> aliases = new HashMap<>();
        game.getSentAgendas().forEach((alias, id) -> aliases.put(id, alias));
        List<PeekedAgenda> peeked = new ArrayList<>();
        for (AiPrompt prompt : Prompts.thisTurn(context)) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isUnowned() || !button.handlerId().startsWith(TOP_AGENDA)) continue;
                String id = StringUtils.removeStart(button.handlerId(), TOP_AGENDA);
                if (!StringUtils.isNumeric(id) || CardMessages.done(context, AGENDA_PEEK + id)) continue;
                String alias = aliases.get(Integer.parseInt(id));
                if (alias == null) continue;
                peeked.add(new PeekedAgenda(prompt, id, alias, AgendaPeek.keepOnTop(game, context.seat(), alias)));
            }
        }
        return peeked.stream()
                .min(Comparator.comparing(PeekedAgenda::keepOnTop))
                .flatMap(agenda -> agenda.press(context));
    }

    private record PeekedAgenda(AiPrompt prompt, String id, String alias, boolean keepOnTop) {

        Optional<AiDecision> press(AiTurnContext context) {
            CardMessages.markDone(context, AGENDA_PEEK + id);
            String handler = (keepOnTop ? TOP_AGENDA : BOTTOM_AGENDA) + id;
            String where = keepOnTop ? "top" : "bottom";
            return prompt.enabledHandler(handler)
                    .map(button -> AiDecision.press(prompt, button, "put " + alias + " on the " + where));
        }
    }

    static Optional<AiDecision> produceWithConstruction(AiTurnContext context, AiPrompt card, int initiative) {
        if (CardMessages.done(context, CONSTRUCTION_PICK_STEP)) return Optional.empty();
        if (!CardMessages.done(context, CONSTRUCTION_PRODUCE_STEP)) {
            CardMessages.markDone(context, CONSTRUCTION_PRODUCE_STEP);
            Optional<String> dock = productionTarget(context, initiative);
            if (dock.isEmpty()) return finish(context, CONSTRUCTION_PICK_STEP);
            Optional<AiDecision> press = CardMessages.pressOnce(
                    context, card, CONSTRUCTION_PRODUCE, "produce at " + dock.get() + " with Construction");
            if (press.isEmpty()) return finish(context, CONSTRUCTION_PICK_STEP);
            CardMessages.markDone(context, StrategyCardRules.CONSTRUCTION_FIRST);
            remember(context, dock.get());
            return press;
        }
        String dock = context.memory().get(TARGET_KEY + context.turnKey()).orElse("");
        Optional<Match> pick =
                Prompts.owned(Prompts.thisTurn(context), context.faction(), (CONSTRUCTION_BUILD + dock)::equals);
        if (pick.isPresent()) {
            CardMessages.markDone(context, CONSTRUCTION_PICK_STEP);
            Tile tile = context.game().getTileByPosition(dock);
            BuildPlan plan = ProductionPlanner.plan(
                    context.game(),
                    context.seat(),
                    tile,
                    0,
                    new DefenseOutlook(context.game(), context.seat()),
                    OpeningOutlook.overSupplyCarriers(context.game(), context.seat(), tile));
            context.memory().put(buildKey(context), context.now() + "|" + dock + "|" + plan.encode());
            return Optional.of(pick.get().press("produce at " + dock + " with Construction"));
        }
        return waitOrFinish(context, CONSTRUCTION_PICK_STEP, "the Construction dock choice");
    }

    private static Optional<String> productionTarget(AiTurnContext context, int initiative) {
        Game game = context.game();
        Player seat = context.seat();
        if (CardVariant.of(game, initiative) != CardVariant.THUNDERS_EDGE) return Optional.empty();
        PlanInputs in = PlanInputs.of(game, seat, PlanMoment.ACTION);
        CardPlan plan = StrategyCardPlanner.plan(in, initiative);
        return plan.benefit(CardUse.CONSTRUCTION_PRODUCE)
                .filter(benefit -> plan.main()
                        .filter(main -> PRODUCTION_USES.contains(main.use()))
                        .isPresent())
                .map(Benefit::target)
                .filter(position -> ConstructionUses.canProduceAt(game, seat, position));
    }

    public static Optional<AiDecision> buildWithConstruction(AiTurnContext context) {
        Optional<ConstructionBuild> build = constructionBuild(context);
        if (build.isEmpty()) return Optional.empty();
        long since = build.get().since() - CLOCK_SKEW_MILLIS;
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since)
                .toList();
        Optional<AiDecision> pay = TacticalRules.payForUnits(context, prompts, CONSTRUCTION_SOURCE);
        if (pay.isPresent()) return pay;
        return TacticalRules.placeUnits(
                context,
                prompts,
                CONSTRUCTION_SOURCE,
                build.get().dock(),
                BuildPlan.decode(build.get().plan()));
    }

    static boolean productionOpen(AiTurnContext context) {
        Optional<ConstructionBuild> build = constructionBuild(context);
        if (build.isEmpty()) return false;
        if (context.now() - build.get().since() > STEP_WAIT_MILLIS) return false;
        String done = DONE_PRODUCING_PREFIX + build.get().dock();
        return context.prompts().stream()
                .filter(prompt -> prompt.createdAtMillis() >= build.get().since() - CLOCK_SKEW_MILLIS)
                .noneMatch(prompt -> prompt.buttons().stream()
                        .anyMatch(button -> done.equals(button.handlerId()) && context.alreadyPressed(prompt, button)));
    }

    static long productionWaitUntil(AiTurnContext context) {
        return constructionBuild(context)
                .map(build -> build.since() + STEP_WAIT_MILLIS)
                .orElse(context.now());
    }

    private record ConstructionBuild(long since, String dock, String plan) {}

    private static Optional<ConstructionBuild> constructionBuild(AiTurnContext context) {
        Optional<String> value = context.memory().get(buildKey(context));
        if (value.isEmpty()) return Optional.empty();
        String[] parts = value.get().split("\\|", 3);
        if (parts.length != 3 || !StringUtils.isNumeric(parts[0])) return Optional.empty();
        long since = Long.parseLong(parts[0]);
        if (context.now() - since > PRODUCTION_WINDOW_MILLIS) {
            context.memory().remove(buildKey(context));
            return Optional.empty();
        }
        return Optional.of(new ConstructionBuild(since, parts[1], parts[2]));
    }

    private static String buildKey(AiTurnContext context) {
        return CONSTRUCTION_BUILD_KEY + context.game().getRound();
    }

    private static void remember(AiTurnContext context, String target) {
        context.memory().put(TARGET_KEY + context.turnKey(), target);
        context.memory().put(STEP_TIME_KEY + context.turnKey(), String.valueOf(context.now()));
    }

    private static Optional<AiDecision> waitOrFinish(AiTurnContext context, String step, String what) {
        long since = context.memory()
                .get(STEP_TIME_KEY + context.turnKey())
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong)
                .orElse(context.now());
        long until = since + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, what));
        return finish(context, step);
    }

    private static Optional<AiDecision> finish(AiTurnContext context, String step) {
        CardMessages.markDone(context, step);
        return Optional.empty();
    }
}
