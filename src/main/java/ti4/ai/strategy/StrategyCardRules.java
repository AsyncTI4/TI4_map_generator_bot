package ti4.ai.strategy;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.ScoringRules;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SpyNetwork;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.trade.TradeCardRules;
import ti4.ai.trade.TradeCardRules.FollowChoice;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

@UtilityClass
public class StrategyCardRules {

    private static final String PLAYED_KEY = "scPlayed|";
    private static final String RESEARCH_KEY = "research|";
    private static final String TOKEN_GAIN_KEY = "tokenGain|";
    private static final String STRUCTURE_KEY = "structure|";
    private static final String READY_KEY = "readyPlanets|";
    private static final String READY_PLAN_KEY = "readyPlan|";
    private static final String PLAN_SEPARATOR = ",";
    private static final String PLACE_PREFIX = "place_";
    private static final String REFRESH_PREFIX = "refresh_";
    private static final String DIPLOMACY_DONE = "deleteButtons_diplomacy";
    private static final int PDS_CODE = 0;
    private static final int SPACE_DOCK_CODE = 1;
    private static final String GET_TECH = "getTech_";
    private static final String RESEARCH_BUTTON = "acquireATechWithSC_first";
    private static final String SPEAKER_PREFIX = "sc_3_assign_speaker_to_";
    static final int LEADERSHIP_TOKENS = 3;
    static final int SECOND_TECH_COST = 6;
    static final int FOLLOW_TECH_COST = 4;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final long PRIMARY_WAIT_MILLIS = 60_000L;
    private static final String WARFARE_FINISHED = "warfareFinished";
    private static final String REMOVE_TOKEN_PREFIX = "removeCCFromBoard_warfare_";
    private static final int PROPAGATION_TOKENS = 3;
    private static final String LEADERSHIP_BUTTON = "leadershipGenerateCCButtons";
    private static final String PROPAGATION_BUTTON = "nekroFollowTech";
    private static final String PROPAGATION_PAYMENT_BUTTON = "nekroTechExhaust";
    private static final int MIN_PROPAGATION_FOLLOW_GAIN = 3;
    private static final long FOLLOW_WAIT_MILLIS = 90_000L;
    private static final long REQUEST_WINDOW_MILLIS = 15 * 60_000L;
    private static final String WARFARE_BUILD_KEY = "warfareBuild|";
    private static final String WARFARE_PLAN_KEY = "warfareBuildPlan|";
    private static final List<String> WARFARE_BUILD_BUTTONS = List.of("warfareBuild", "warfareTeBuild");
    private static final String DIPLOMACY_READY_BUTTON = "diploRefresh2";
    private static final String TRADE_REFRESH_BUTTON = "sc_refresh";
    private static final String TRADE_TOKEN_BUTTON = "sc_trade_follow";
    private static final String TRADE_GRANT = "tradeGrant";
    private static final String POLITICS_DRAW_BUTTON = "sc_ac_draw";
    private static final String HOLD_NO_ACTION_CARDS = "dont_fsn";
    private static final int POLITICS_DRAW = 2;
    private static final int EXTRA_TOKENS = 2;
    private static final int MIN_HAND_ROOM_EXTRA = 2;
    private static final List<StrategyCard> FOLLOW_PRIORITY = List.of(
            StrategyCard.IMPERIAL,
            StrategyCard.TECHNOLOGY,
            StrategyCard.DIPLOMACY,
            StrategyCard.WARFARE,
            StrategyCard.CONSTRUCTION,
            StrategyCard.POLITICS,
            StrategyCard.TRADE);
    private static final List<StrategyCard> SPY_NETWORK_PRIORITY = List.of(
            StrategyCard.IMPERIAL,
            StrategyCard.POLITICS,
            StrategyCard.TECHNOLOGY,
            StrategyCard.DIPLOMACY,
            StrategyCard.WARFARE,
            StrategyCard.CONSTRUCTION,
            StrategyCard.TRADE);

    public static AiDecision play(AiTurnContext context, AiPrompt prompt, PromptButton button, int initiative) {
        context.memory().put(PLAYED_KEY + context.turnKey(), String.valueOf(initiative));
        if (StrategyCard.of(context.game(), initiative) == StrategyCard.TRADE) TradeCardRules.recordPlay(context);
        return AiDecision.press(prompt, button, "play a strategy card");
    }

    public static boolean playedThisTurn(AiTurnContext context) {
        return context.memory().has(PLAYED_KEY + context.turnKey());
    }

    public static Optional<AiDecision> resolvePrimary(AiTurnContext context) {
        Optional<Integer> initiative = context.memory()
                .get(PLAYED_KEY + context.turnKey())
                .filter(StringUtils::isNumeric)
                .map(Integer::parseInt);
        if (initiative.isEmpty()) return Optional.empty();
        Optional<Long> pendingSince = pendingSince(context);
        if (pendingSince.isPresent()) {
            long until = pendingSince.get() + PRIMARY_WAIT_MILLIS;
            if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "a strategy card step"));
            giveUpPendingSteps(context);
        }
        Optional<AiPrompt> card = CardMessages.playedThisTurn(context, initiative.get());
        return switch (StrategyCard.of(context.game(), initiative.get())) {
            case IMPERIAL -> imperial(context, card);
            case TECHNOLOGY -> technology(context, card);
            case LEADERSHIP -> leadership(context, card);
            case POLITICS -> politics(context, card);
            case CONSTRUCTION -> construction(context, card);
            case DIPLOMACY -> diplomacy(context, card);
            case WARFARE -> warfare(context, card, initiative.get());
            case TRADE -> trade(context);
            default -> Optional.empty();
        };
    }

    private static Optional<AiDecision> trade(AiTurnContext context) {
        if (CardMessages.done(context, TRADE_GRANT)) return Optional.empty();
        CardMessages.markDone(context, TRADE_GRANT);
        if (AiSettings.isTradingEnabled()) return TradeCardRules.announce(context);
        return Optional.of(new AiDecision.Announce("🤖 " + context.seat().getRepresentationNoPing()
                + " lets every other player replenish their commodities with **Trade** without spending a command"
                + " token."));
    }

    private static Optional<AiDecision> warfare(AiTurnContext context, Optional<AiPrompt> card, int initiative) {
        if (card.isEmpty() || CardMessages.done(context, WARFARE_FINISHED)) return Optional.empty();
        Game game = context.game();
        boolean thundersEdge = game.getStrategyCardModelByInitiative(initiative)
                .map(model -> model.usesAutomationForSCID("te6warfare"))
                .orElse(false);
        if (!thundersEdge) return removeTokenWithWarfare(context, card.get());
        if (!CardMessages.done(context, "warfarePlan")) {
            CardMessages.markDone(context, "warfarePlan");
            Optional<TacticalPlan> plan = TacticalPlanner.bestForWarfare(game, context.seat());
            if (plan.isEmpty()) {
                CardMessages.markDone(context, WARFARE_FINISHED);
                return Optional.empty();
            }
            TacticalRules.remember(context, plan.get());
            context.memory().put(warfareStepKey(context), String.valueOf(context.now()));
            return CardMessages.pressOnce(
                    context,
                    card.get(),
                    "primaryOfTeWarfare",
                    "use Warfare for a tactical action: " + TacticalRules.describe(plan.get()));
        }
        if (!CardMessages.done(context, "warfareBegin")) {
            Optional<Match> begin =
                    Prompts.owned(Prompts.thisTurn(context), context.faction(), "beginTacticalTeWarfare"::equals);
            if (begin.isEmpty()) return waitForWarfare(context, "the Warfare prompt");
            CardMessages.markDone(context, "warfareBegin");
            context.memory().put(warfareStepKey(context), String.valueOf(context.now()));
            return Optional.of(begin.get().press("start the Warfare tactical action"));
        }
        if (TacticalRules.pickingSystem(context)) return TacticalRules.start(context);
        if (!TacticalRules.activatedThisTurn(context)) return waitForWarfare(context, "the system picker");
        CardMessages.markDone(context, WARFARE_FINISHED);
        return Optional.empty();
    }

    private static Optional<AiDecision> waitForWarfare(AiTurnContext context, String what) {
        long since = context.memory()
                .get(warfareStepKey(context))
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong)
                .orElse(context.now());
        long until = since + PRIMARY_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, what));
        CardMessages.markDone(context, WARFARE_FINISHED);
        return Optional.empty();
    }

    private static String warfareStepKey(AiTurnContext context) {
        return "warfareStep|" + context.turnKey();
    }

    private static Optional<AiDecision> removeTokenWithWarfare(AiTurnContext context, AiPrompt card) {
        if (!CardMessages.done(context, "warfareRemove")) {
            CardMessages.markDone(context, "warfareRemove");
            return CardMessages.pressOnce(context, card, "primaryOfWarfare", "remove a command token with Warfare");
        }
        Game game = context.game();
        Player seat = context.seat();
        List<PromptButton> removals = Prompts.thisTurn(context).stream()
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .filter(button -> button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith(REMOVE_TOKEN_PREFIX))
                .toList();
        if (!removals.isEmpty() && !CardMessages.done(context, "warfareRemoved")) {
            CardMessages.markDone(context, "warfareRemoved");
            CardMessages.markDone(context, WARFARE_FINISHED);
            Tile home = seat.getHomeSystemTile();
            PromptButton choice = removals.stream()
                    .filter(button -> home != null && positionOf(button).equals(home.getPosition()))
                    .findFirst()
                    .orElse(removals.getFirst());
            AiPrompt prompt = Prompts.thisTurn(context).stream()
                    .filter(candidate -> candidate.buttons().contains(choice))
                    .findFirst()
                    .orElseThrow();
            return Optional.of(AiDecision.press(prompt, choice, "take back a command token with Warfare"));
        }
        CardMessages.markDone(context, WARFARE_FINISHED);
        return Optional.empty();
    }

    private static String positionOf(PromptButton removeButton) {
        String rest = StringUtils.removeStart(removeButton.handlerId(), REMOVE_TOKEN_PREFIX);
        return StringUtils.substringBefore(rest, "_");
    }

    private static Optional<Long> pendingSince(AiTurnContext context) {
        return java.util.stream.Stream.of(
                        PaymentRules.pendingSince(context),
                        request(context, RESEARCH_KEY).map(values -> values[0]),
                        request(context, STRUCTURE_KEY).map(values -> values[0]),
                        request(context, READY_KEY).map(values -> values[0]),
                        tokenGain(context).map(TokenGain::since))
                .flatMap(Optional::stream)
                .min(Long::compare);
    }

    private static void giveUpPendingSteps(AiTurnContext context) {
        PaymentRules.forget(context);
        for (String prefix : List.of(RESEARCH_KEY, STRUCTURE_KEY, READY_KEY, READY_PLAN_KEY, TOKEN_GAIN_KEY)) {
            context.memory().remove(requestKey(context, prefix));
        }
    }

    public static Optional<AiDecision> follow(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        List<Integer> unfollowed = game.getPlayedSCs().stream()
                .filter(initiative -> !seat.getSCs().contains(initiative) && !seat.hasFollowedSC(initiative))
                .sorted(Comparator.comparingInt(initiative -> followRank(context, StrategyCard.of(game, initiative))))
                .toList();
        for (int initiative : unfollowed) {
            Optional<AiPrompt> card = CardMessages.cardMessage(context, initiative);
            if (card.isEmpty()) continue;
            Optional<AiDecision> decision =
                    switch (StrategyCard.of(game, initiative)) {
                        case IMPERIAL -> followImperial(context, card.get());
                        case TECHNOLOGY ->
                            busyPaying(context) ? Optional.empty() : followTechnology(context, card.get());
                        case DIPLOMACY -> busyPaying(context) ? Optional.empty() : followDiplomacy(context, card.get());
                        case WARFARE -> busyPaying(context) ? Optional.empty() : followWarfare(context, card.get());
                        case CONSTRUCTION -> followConstruction(context, card.get());
                        case POLITICS -> followPolitics(context, card.get());
                        case TRADE -> followTrade(context, card.get(), initiative);
                        case LEADERSHIP ->
                            busyPaying(context) ? Optional.empty() : followLeadership(context, card.get());
                        default -> Optional.empty();
                    };
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    public static boolean waitsToFollow(AiTurnContext context, int initiative) {
        StrategyCard card = StrategyCard.of(context.game(), initiative);
        return (card == StrategyCard.LEADERSHIP
                        || card == StrategyCard.TECHNOLOGY
                        || card == StrategyCard.WARFARE
                        || card == StrategyCard.DIPLOMACY)
                && busyPaying(context);
    }

    private static int followRank(AiTurnContext context, StrategyCard card) {
        List<StrategyCard> priority =
                chasesSpyNetwork(context.game(), context.seat()) ? SPY_NETWORK_PRIORITY : FOLLOW_PRIORITY;
        int rank = priority.indexOf(card);
        return rank < 0 ? priority.size() : rank;
    }

    private static boolean chasesSpyNetwork(Game game, Player seat) {
        return !holdsNoActionCards(seat) && SpyNetwork.withinReach(game, seat, POLITICS_DRAW);
    }

    private static boolean holdsNoActionCards(Player seat) {
        return seat.getSecretsUnscored().containsKey(HOLD_NO_ACTION_CARDS);
    }

    private static boolean wantsToFollow(AiTurnContext context, StrategyCard card) {
        Game game = context.game();
        Player seat = context.seat();
        return switch (card) {
            case IMPERIAL -> hasRoomForSecret(seat);
            case TECHNOLOGY ->
                cannotResearch(seat)
                        ? propagationWorthFollowing(game, seat)
                                && ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(FOLLOW_TECH_COST))
                                        .isPresent()
                        : worthPayingForResearch(context, FOLLOW_TECH_COST, ResearchPolicy.WORTH_FOLLOWING_FOR);
            case DIPLOMACY -> DiplomacyUse.worthFollowing(game, seat);
            case WARFARE -> homeBuild(game, seat).isPresent();
            case CONSTRUCTION -> StructurePolicy.nearsStructureObjective(game, seat);
            case POLITICS -> chasesSpyNetwork(game, seat);
            default -> false;
        };
    }

    private static int tokensSavedForBetterCards(AiTurnContext context, StrategyCard card) {
        Game game = context.game();
        int rank = followRank(context, card);
        int saved = 0;
        for (Player other : game.getRealPlayers()) {
            if (other.getUserID().equals(context.seat().getUserID())) continue;
            for (int initiative : other.getSCs()) {
                if (!stillToFollow(context, initiative)) continue;
                StrategyCard better = StrategyCard.of(game, initiative);
                if (followRank(context, better) < rank && wantsToFollow(context, better)) saved++;
            }
        }
        return saved;
    }

    private static boolean stillToFollow(AiTurnContext context, int initiative) {
        if (!context.game().getPlayedSCs().contains(initiative)) return true;
        return !context.seat().hasFollowedSC(initiative) && waitsToFollow(context, initiative);
    }

    private static Optional<AiDecision> followDiplomacy(AiTurnContext context, AiPrompt card) {
        if (!hasSpareStrategyToken(context, StrategyCard.DIPLOMACY)
                || !wantsToFollow(context, StrategyCard.DIPLOMACY)
                || request(context, READY_KEY).isPresent()) {
            return Optional.empty();
        }
        Optional<AiDecision> press =
                CardMessages.pressOnce(context, card, DIPLOMACY_READY_BUTTON, "follow Diplomacy to ready planets");
        press.ifPresent(
                decision -> requestReadying(context, DiplomacyUse.bestToFollow(context.game(), context.seat())));
        return press;
    }

    private static void requestReadying(AiTurnContext context, Optional<DiplomacyUse.Choice> plan) {
        context.memory().put(requestKey(context, READY_KEY), context.now() + "|" + DiplomacyUse.PLANETS_TO_READY);
        plan.ifPresent(choice -> rememberReadyPlan(context, choice.planets()));
    }

    private static void rememberReadyPlan(AiTurnContext context, List<String> planets) {
        context.memory().put(requestKey(context, READY_PLAN_KEY), String.join(PLAN_SEPARATOR, planets));
    }

    private static List<String> readyPlan(AiTurnContext context) {
        return context.memory()
                .get(requestKey(context, READY_PLAN_KEY))
                .map(plan -> List.of(StringUtils.split(plan, PLAN_SEPARATOR)))
                .orElse(List.of());
    }

    private static Optional<AiDecision> followPolitics(AiTurnContext context, AiPrompt card) {
        if (!wantsPoliticsCards(context)) return Optional.empty();
        return CardMessages.pressOnce(context, card, POLITICS_DRAW_BUTTON, "follow Politics to draw action cards");
    }

    private static boolean wantsPoliticsCards(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        int limit = ButtonHelper.getACLimit(game, seat);
        int room = limit - seat.getAcCount();
        int strategyTokens = seat.getStrategicCC();
        if (holdsNoActionCards(seat) || strategyTokens == 0 || room < 1) {
            return false;
        }
        if (chasesSpyNetwork(game, seat)) {
            return strategyTokens + seat.getTacticalCC() - 1
                    >= ScoringReserve.of(game, seat).tokens();
        }
        if (SpyNetwork.needsCards(game, seat)) {
            return room >= POLITICS_DRAW && hasSpareStrategyToken(context, StrategyCard.POLITICS);
        }
        return room >= MIN_HAND_ROOM_EXTRA
                && hasSpareStrategyToken(context, StrategyCard.POLITICS)
                && strategyTokens - tokensSavedForBetterCards(context, StrategyCard.POLITICS) >= EXTRA_TOKENS;
    }

    private static Optional<AiDecision> followWarfare(AiTurnContext context, AiPrompt card) {
        Game game = context.game();
        Player seat = context.seat();
        if (!hasSpareStrategyToken(context, StrategyCard.WARFARE)) return Optional.empty();
        Optional<BuildPlan> plan = homeBuild(game, seat);
        if (plan.isEmpty()) return Optional.empty();
        Optional<PromptButton> button = WARFARE_BUILD_BUTTONS.stream()
                .map(card::enabledHandler)
                .flatMap(Optional::stream)
                .findFirst();
        if (button.isEmpty() || context.alreadyPressed(card, button.get())) return Optional.empty();
        context.memory().put(requestKey(context, WARFARE_BUILD_KEY), context.now() + "|0");
        context.memory().put(requestKey(context, WARFARE_PLAN_KEY), plan.get().encode());
        return Optional.of(AiDecision.press(card, button.get(), "follow Warfare to produce units at home"));
    }

    private static Optional<BuildPlan> homeBuild(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        if (home == null || !TacticalPlanner.worthProducingAt(game, seat, home)) return Optional.empty();
        BuildPlan plan = ProductionPlanner.plan(game, seat, home);
        return plan.isEmpty() ? Optional.empty() : Optional.of(plan);
    }

    public static Optional<AiDecision> buildWithWarfare(AiTurnContext context) {
        Optional<long[]> request = request(context, WARFARE_BUILD_KEY);
        Tile home = context.seat().getHomeSystemTile();
        if (request.isEmpty() || home == null) return Optional.empty();
        List<AiPrompt> prompts = CardMessages.hiddenSince(context, request.get()[0] - CLOCK_SKEW_MILLIS);
        Optional<AiDecision> pay = TacticalRules.payForUnits(context, prompts, TacticalRules.WARFARE_SOURCE);
        if (pay.isPresent()) return pay;
        Optional<BuildPlan> plan =
                context.memory().get(requestKey(context, WARFARE_PLAN_KEY)).flatMap(BuildPlan::decode);
        return TacticalRules.placeUnits(context, prompts, TacticalRules.WARFARE_SOURCE, home.getPosition(), plan);
    }

    private static Optional<AiDecision> followTrade(AiTurnContext context, AiPrompt card, int initiative) {
        Player seat = context.seat();
        Player holder = Helper.getPlayerWithThisSC(context.game(), initiative);
        if (!AiSettings.isTradingEnabled()) {
            if (!AiSeats.isAiSeat(holder) || seat.getCommodities() >= seat.getCommoditiesTotal()) {
                return Optional.empty();
            }
            return CardMessages.pressOnce(
                    context, card, TRADE_REFRESH_BUTTON, "follow Trade for free to replenish commodities");
        }
        FollowChoice choice = TradeCardRules.followChoice(context, holder);
        Optional<AiDecision> press =
                switch (choice) {
                    case FREE ->
                        CardMessages.pressOnce(
                                context, card, TRADE_REFRESH_BUTTON, "follow Trade for free to replenish commodities");
                    case TOKEN ->
                        hasSpareStrategyToken(context, StrategyCard.TRADE)
                                ? CardMessages.pressOnce(
                                        context,
                                        card,
                                        TRADE_TOKEN_BUTTON,
                                        "follow Trade with a strategy token to replenish commodities")
                                : Optional.empty();
                    case DECLINE -> Optional.empty();
                };
        press.ifPresent(ignored -> TradeCardRules.followPressed(context, holder, choice));
        return press;
    }

    private static boolean busyPaying(AiTurnContext context) {
        return java.util.stream.Stream.of(
                        PaymentRules.pendingSince(context),
                        tokenGain(context).map(TokenGain::since),
                        request(context, RESEARCH_KEY).map(values -> values[0]))
                .flatMap(Optional::stream)
                .anyMatch(since -> context.now() - since <= FOLLOW_WAIT_MILLIS);
    }

    public static Optional<AiDecision> chooseTechnology(AiTurnContext context) {
        Optional<long[]> request = request(context, RESEARCH_KEY);
        if (request.isEmpty()) return Optional.empty();
        long since = request.get()[0] - CLOCK_SKEW_MILLIS;
        int cost = (int) request.get()[1];
        Map<String, Match> options = new LinkedHashMap<>();
        for (AiPrompt prompt : CardMessages.hiddenSince(context, since)) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(GET_TECH)) continue;
                String alias = StringUtils.substringBefore(StringUtils.removeStart(button.handlerId(), GET_TECH), "__");
                options.putIfAbsent(alias, new Match(prompt, button));
            }
        }
        if (options.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        context.memory().remove(requestKey(context, RESEARCH_KEY));
        Optional<String> best = ResearchPolicy.best(game, seat, options.keySet());
        if (best.isEmpty()) return Optional.empty();
        if (cost > 0) {
            Optional<Wallet.Payment> payment = ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(cost))
                    .or(() -> Wallet.of(game, seat).plan(SpendCost.resources(cost)));
            if (payment.isEmpty()) return Optional.empty();
            PaymentRules.expect(context, "a technology", payment.get(), PaymentRules.TECHNOLOGY_DONE);
        } else {
            PaymentRules.expectNothing(context, "a technology", PaymentRules.TECHNOLOGY_DONE);
        }
        return Optional.of(options.get(best.get()).press("research " + best.get()));
    }

    public static Optional<AiDecision> placeStructure(AiTurnContext context) {
        Optional<long[]> request = request(context, STRUCTURE_KEY);
        if (request.isEmpty()) return Optional.empty();
        long since = request.get()[0] - CLOCK_SKEW_MILLIS;
        String unit = request.get()[1] == SPACE_DOCK_CODE ? StructurePolicy.SPACE_DOCK : StructurePolicy.PDS;
        String prefix = PLACE_PREFIX + unit + "_";
        Map<String, Match> options = new LinkedHashMap<>();
        for (AiPrompt prompt : CardMessages.hiddenSince(context, since)) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isOwnedBy(context.faction()) && button.handlerId().startsWith(prefix)) {
                    options.putIfAbsent(StringUtils.removeStart(button.handlerId(), prefix), new Match(prompt, button));
                }
            }
        }
        if (options.isEmpty()) return Optional.empty();
        context.memory().remove(requestKey(context, STRUCTURE_KEY));
        return StructurePolicy.planetFor(context.game(), context.seat(), unit, options.keySet())
                .map(planet -> options.get(planet).press("place a " + unit + " on " + planet));
    }

    public static Optional<AiDecision> readyPlanets(AiTurnContext context) {
        Optional<long[]> request = request(context, READY_KEY);
        if (request.isEmpty()) return Optional.empty();
        List<AiPrompt> prompts = CardMessages.hiddenSince(context, request.get()[0] - CLOCK_SKEW_MILLIS);
        Optional<Match> done = Prompts.unowned(prompts, handlerId -> handlerId.startsWith(DIPLOMACY_DONE));
        if (done.isEmpty()) return Optional.empty();
        int remaining = (int) request.get()[1];
        if (remaining > 0) {
            Map<String, Match> offered = refreshButtons(context, prompts);
            Optional<String> planet = nextPlanetToReady(context, offered.keySet(), remaining);
            if (planet.isPresent()) {
                context.memory().put(requestKey(context, READY_KEY), request.get()[0] + "|" + (remaining - 1));
                return Optional.of(offered.get(planet.get()).press("ready a planet with Diplomacy"));
            }
        }
        context.memory().remove(requestKey(context, READY_KEY));
        context.memory().remove(requestKey(context, READY_PLAN_KEY));
        return Optional.of(done.get().press("finish readying planets"));
    }

    private static Map<String, Match> refreshButtons(AiTurnContext context, List<AiPrompt> prompts) {
        Map<String, Match> offered = new LinkedHashMap<>();
        for (AiPrompt prompt : prompts) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(REFRESH_PREFIX)) continue;
                offered.putIfAbsent(
                        StringUtils.removeStart(button.handlerId(), REFRESH_PREFIX), new Match(prompt, button));
            }
        }
        return offered;
    }

    private static Optional<String> nextPlanetToReady(AiTurnContext context, Set<String> offered, int remaining) {
        Optional<String> planned =
                readyPlan(context).stream().filter(offered::contains).findFirst();
        if (planned.isPresent()) return planned;
        Optional<DiplomacyUse.Choice> replanned = DiplomacyUse.best(context.game(), context.seat(), offered, remaining);
        replanned.ifPresent(choice -> rememberReadyPlan(context, choice.planets()));
        return replanned.map(choice -> choice.planets().getFirst());
    }

    public static Optional<AiDecision> gainTokens(AiTurnContext context) {
        Optional<TokenGain> request = tokenGain(context);
        if (request.isEmpty()) return Optional.empty();
        TokenGain gain = request.get();
        long since = gain.since() - CLOCK_SKEW_MILLIS;
        if (gain.purchased() > 0 && PaymentRules.isPending(context)) return openPropagationPayment(context, since);
        int target = gain.free() + (PaymentRules.wasPaid(context, gain.since()) ? gain.purchased() : 0);
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : CardMessages.hiddenSince(context, since)) {
            Optional<PromptButton> done =
                    prompt.firstEnabled(button -> (button.isOwnedBy(context.faction()) || button.isUnowned())
                            && button.handlerId().startsWith("deleteButtons")
                            && button.label().startsWith("Done Gaining"));
            if (done.isEmpty()) continue;
            if (CommandTokenPolicy.netGainSoFar(game, seat) < target) {
                Optional<PromptButton> grow = prompt.enabledHandler(CommandTokenPolicy.poolToGrow(game, seat));
                if (grow.isPresent()) return Optional.of(AiDecision.press(prompt, grow.get(), "gain a command token"));
            }
            context.memory().remove(requestKey(context, TOKEN_GAIN_KEY));
            return Optional.of(AiDecision.press(prompt, done.get(), "finish gaining command tokens"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> openPropagationPayment(AiTurnContext context, long since) {
        for (AiPrompt prompt : CardMessages.hiddenSince(context, since)) {
            Optional<PromptButton> open = prompt.firstEnabled(button ->
                    PROPAGATION_PAYMENT_BUTTON.equals(button.handlerId()) && !context.alreadyPressed(prompt, button));
            if (open.isPresent()) {
                return Optional.of(AiDecision.press(prompt, open.get(), "open the payment for Propagation"));
            }
        }
        return Optional.empty();
    }

    private static void requestTokens(AiTurnContext context, int free, int purchased) {
        context.memory()
                .put(requestKey(context, TOKEN_GAIN_KEY), new TokenGain(context.now(), free, purchased).encode());
    }

    private static Optional<TokenGain> tokenGain(AiTurnContext context) {
        Optional<TokenGain> gain =
                context.memory().get(requestKey(context, TOKEN_GAIN_KEY)).flatMap(TokenGain::decode);
        if (gain.isPresent() && context.now() - gain.get().since() > REQUEST_WINDOW_MILLIS) {
            context.memory().remove(requestKey(context, TOKEN_GAIN_KEY));
            return Optional.empty();
        }
        return gain;
    }

    private record TokenGain(long since, int free, int purchased) {

        String encode() {
            return since + "|" + free + "|" + purchased;
        }

        static Optional<TokenGain> decode(String value) {
            String[] parts = value.split("\\|");
            if (parts.length != 3 || !StringUtils.isNumeric(parts[0])) return Optional.empty();
            if (!StringUtils.isNumeric(parts[1]) || !StringUtils.isNumeric(parts[2])) return Optional.empty();
            return Optional.of(
                    new TokenGain(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
    }

    private static Optional<AiDecision> imperial(AiTurnContext context, Optional<AiPrompt> card) {
        Game game = context.game();
        Player seat = context.seat();
        String faction = context.faction();
        if (!CardMessages.done(context, "imperialScore")) {
            Optional<Match> scoring =
                    Prompts.owned(Prompts.thisTurn(context), faction, id -> id.startsWith(Constants.PO_SCORING));
            if (scoring.isPresent()) {
                CardMessages.markDone(context, "imperialScore");
                AiPrompt prompt = scoring.get().prompt();
                List<PromptButton> buttons = prompt.enabledButtons().stream()
                        .filter(button ->
                                button.isOwnedBy(faction) && button.handlerId().startsWith(Constants.PO_SCORING))
                        .toList();
                Optional<ObjectivePolicy.ScoringChoice> best = ObjectivePolicy.bestScorablePublic(game, seat, buttons);
                if (best.isPresent()) {
                    return Optional.of(ScoringRules.scoreAndExpectPayment(
                            context, prompt, best.get(), "score a public objective with Imperial"));
                }
            } else if (card.isPresent()
                    && !ObjectivePolicy.scorablePublics(game, seat).isEmpty()) {
                Optional<AiDecision> open = CardMessages.pressOnce(
                        context, card.get(), "scoreAnObjective", "choose an objective to score with Imperial");
                if (open.isPresent()) return open;
                CardMessages.markDone(context, "imperialScore");
            } else {
                CardMessages.markDone(context, "imperialScore");
            }
        }
        if (card.isPresent() && !CardMessages.done(context, "imperialPoint")) {
            CardMessages.markDone(context, "imperialPoint");
            if (seat.controlsMecatol(true)) {
                return CardMessages.pressOnce(
                        context, card.get(), "score_imperial", "score the Imperial point for Mecatol Rex");
            }
            if (seat.getSoScored() < seat.getMaxSOCount()) {
                return CardMessages.pressOnce(
                        context, card.get(), "sc_draw_so", "draw a secret objective with Imperial");
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> technology(AiTurnContext context, Optional<AiPrompt> card) {
        if (card.isEmpty()) return Optional.empty();
        if (cannotResearch(context.seat())) return propagation(context, card.get());
        if (!CardMessages.done(context, "techFirst")) {
            CardMessages.markDone(context, "techFirst");
            return requestResearch(context, card.get(), 0, "research a technology");
        }
        if (!CardMessages.done(context, "techSecond")) {
            CardMessages.markDone(context, "techSecond");
            if (worthPayingForResearch(context, SECOND_TECH_COST, ResearchPolicy.WORTH_PAYING_FOR)) {
                return requestResearch(context, card.get(), SECOND_TECH_COST, "research a second technology");
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> propagation(AiTurnContext context, AiPrompt card) {
        if (CardMessages.done(context, "propagation")) return Optional.empty();
        CardMessages.markDone(context, "propagation");
        int gain = Math.min(PROPAGATION_TOKENS, reinforcements(context));
        Optional<PromptButton> button = card.firstEnabled(candidate ->
                candidate.isOwnedBy(context.faction()) && PROPAGATION_BUTTON.equals(candidate.handlerId()));
        if (button.isEmpty() || gain == 0) return Optional.empty();
        requestTokens(context, gain, 0);
        return Optional.of(AiDecision.press(card, button.get(), "gain command tokens with Propagation"));
    }

    private static int reinforcements(AiTurnContext context) {
        return reinforcements(context.game(), context.seat());
    }

    static int reinforcements(Game game, Player seat) {
        return Math.max(0, seat.getCommandTokenLimit() - Helper.getCCCount(game, seat.getColor()));
    }

    private static Optional<AiDecision> leadership(AiTurnContext context, Optional<AiPrompt> card) {
        if (card.isEmpty() || CardMessages.done(context, "leadership")) return Optional.empty();
        CardMessages.markDone(context, "leadership");
        Game game = context.game();
        Player seat = context.seat();
        int reinforcements = reinforcements(context);
        int free = Math.min(LEADERSHIP_TOKENS, reinforcements);
        Optional<TokenPurchase.Purchase> purchase = TokenPurchase.best(game, seat, reinforcements - free);
        Optional<AiDecision> press =
                CardMessages.pressOnce(context, card.get(), LEADERSHIP_BUTTON, "gain command tokens with Leadership");
        if (press.isPresent()) {
            requestTokens(
                    context, free, purchase.map(TokenPurchase.Purchase::tokens).orElse(0));
            if (purchase.isPresent()) {
                PaymentRules.expect(context, "Leadership", purchase.get().payment(), PaymentRules.LEADERSHIP_DONE);
            } else {
                PaymentRules.expectNothing(context, "Leadership", PaymentRules.LEADERSHIP_DONE);
            }
        }
        return press;
    }

    private static Optional<AiDecision> followLeadership(AiTurnContext context, AiPrompt card) {
        Optional<PromptButton> button = card.enabledHandler(LEADERSHIP_BUTTON);
        if (button.isEmpty() || context.alreadyPressed(card, button.get())) return Optional.empty();
        Optional<TokenPurchase.Purchase> purchase =
                TokenPurchase.best(context.game(), context.seat(), reinforcements(context));
        if (purchase.isEmpty()) return Optional.empty();
        requestTokens(context, 0, purchase.get().tokens());
        PaymentRules.expect(context, "Leadership", purchase.get().payment(), PaymentRules.LEADERSHIP_DONE);
        return Optional.of(AiDecision.press(card, button.get(), "follow Leadership to buy command tokens"));
    }

    private static Optional<AiDecision> politics(AiTurnContext context, Optional<AiPrompt> card) {
        if (!CardMessages.done(context, "speaker")) {
            Optional<AiPrompt> speakerPrompt = Prompts.thisTurn(context).stream()
                    .filter(prompt -> prompt.firstEnabled(StrategyCardRules::isSpeakerChoice)
                            .isPresent())
                    .findFirst();
            if (speakerPrompt.isPresent()) {
                CardMessages.markDone(context, "speaker");
                Optional<PromptButton> choice = speakerChoice(context, speakerPrompt.get());
                if (choice.isPresent()) {
                    return Optional.of(
                            AiDecision.press(speakerPrompt.get(), choice.get(), "choose the speaker with Politics"));
                }
            }
        }
        if (card.isPresent() && !CardMessages.done(context, "politicsCards")) {
            CardMessages.markDone(context, "politicsCards");
            return CardMessages.pressOnce(context, card.get(), "sc_ac_draw", "draw action cards with Politics");
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> construction(AiTurnContext context, Optional<AiPrompt> card) {
        if (card.isEmpty()) return Optional.empty();
        for (String step : List.of("constructionFirst", "constructionSecond")) {
            if (CardMessages.done(context, step)) continue;
            CardMessages.markDone(context, step);
            Optional<String> unit = "constructionFirst".equals(step)
                    ? StructurePolicy.next(context.game(), context.seat())
                    : StructurePolicy.pds(context.game(), context.seat());
            if (unit.isEmpty()) return Optional.empty();
            return requestStructure(context, card.get(), unit.get(), "place a structure with Construction");
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> diplomacy(AiTurnContext context, Optional<AiPrompt> card) {
        if (card.isEmpty() || CardMessages.done(context, "diplomacyReady")) return Optional.empty();
        CardMessages.markDone(context, "diplomacyReady");
        Player seat = context.seat();
        if (seat.getExhaustedPlanets().isEmpty()) return Optional.empty();
        Optional<AiDecision> press =
                CardMessages.pressOnce(context, card.get(), DIPLOMACY_READY_BUTTON, "ready planets with Diplomacy");
        press.ifPresent(decision ->
                requestReadying(context, DiplomacyUse.best(context.game(), seat, seat.getExhaustedPlanets())));
        return press;
    }

    private static Optional<AiDecision> followConstruction(AiTurnContext context, AiPrompt card) {
        Game game = context.game();
        Player seat = context.seat();
        if (!hasSpareStrategyToken(context, StrategyCard.CONSTRUCTION)
                || !StructurePolicy.nearsStructureObjective(game, seat)) {
            return Optional.empty();
        }
        Optional<PromptButton> button = card.enabledHandler("construction_" + StructurePolicy.PDS);
        if (button.isEmpty() || context.alreadyPressed(card, button.get())) return Optional.empty();
        Optional<String> unit = StructurePolicy.next(game, seat);
        if (unit.isEmpty()) return Optional.empty();
        return requestStructure(context, card, unit.get(), "follow Construction to place a structure");
    }

    private static Optional<AiDecision> requestStructure(
            AiTurnContext context, AiPrompt card, String unit, String reason) {
        Optional<PromptButton> button = card.enabledHandler("construction_" + unit);
        if (button.isEmpty()) return Optional.empty();
        int code = StructurePolicy.SPACE_DOCK.equals(unit) ? SPACE_DOCK_CODE : PDS_CODE;
        context.memory().put(requestKey(context, STRUCTURE_KEY), context.now() + "|" + code);
        return Optional.of(AiDecision.press(card, button.get(), reason));
    }

    private static boolean isSpeakerChoice(PromptButton button) {
        return button.handlerId().startsWith(SPEAKER_PREFIX);
    }

    private static Optional<PromptButton> speakerChoice(AiTurnContext context, AiPrompt prompt) {
        String faction = context.faction();
        List<PromptButton> choices = prompt.enabledButtons().stream()
                .filter(button -> button.isOwnedBy(faction) && isSpeakerChoice(button))
                .toList();
        Optional<PromptButton> self = choices.stream()
                .filter(button -> button.handlerId().equals(SPEAKER_PREFIX + faction))
                .findFirst();
        if (self.isPresent()) return self;
        Game game = context.game();
        return choices.stream().min(Comparator.comparingInt(button -> victoryPointsOf(game, button)));
    }

    private static int victoryPointsOf(Game game, PromptButton speakerButton) {
        Player player =
                game.getPlayerFromColorOrFaction(StringUtils.removeStart(speakerButton.handlerId(), SPEAKER_PREFIX));
        return player == null ? Integer.MAX_VALUE : player.getTotalVictoryPoints();
    }

    private static Optional<AiDecision> followImperial(AiTurnContext context, AiPrompt card) {
        if (!hasSpareStrategyToken(context, StrategyCard.IMPERIAL) || !hasRoomForSecret(context.seat())) {
            return Optional.empty();
        }
        return CardMessages.pressOnce(context, card, "sc_draw_so", "follow Imperial to draw a secret objective");
    }

    private static Optional<AiDecision> followTechnology(AiTurnContext context, AiPrompt card) {
        if (cannotResearch(context.seat())) return followPropagation(context, card);
        if (!hasSpareStrategyToken(context, StrategyCard.TECHNOLOGY)
                || !worthPayingForResearch(context, FOLLOW_TECH_COST, ResearchPolicy.WORTH_FOLLOWING_FOR)) {
            return Optional.empty();
        }
        Optional<PromptButton> button = card.enabledHandler(RESEARCH_BUTTON);
        if (button.isEmpty() || context.alreadyPressed(card, button.get())) return Optional.empty();
        context.memory().put(requestKey(context, RESEARCH_KEY), context.now() + "|" + FOLLOW_TECH_COST);
        return Optional.of(AiDecision.press(card, button.get(), "follow Technology to research"));
    }

    private static Optional<AiDecision> followPropagation(AiTurnContext context, AiPrompt card) {
        int gain = propagationGain(context);
        if (!hasSpareStrategyToken(context, StrategyCard.TECHNOLOGY) || gain < MIN_PROPAGATION_FOLLOW_GAIN) {
            return Optional.empty();
        }
        Optional<PromptButton> button = card.firstEnabled(candidate ->
                candidate.isOwnedBy(context.faction()) && PROPAGATION_BUTTON.equals(candidate.handlerId()));
        if (button.isEmpty() || context.alreadyPressed(card, button.get())) return Optional.empty();
        Optional<Wallet.Payment> payment =
                ScoringReserve.planAfterReserve(context.game(), context.seat(), SpendCost.resources(FOLLOW_TECH_COST));
        if (payment.isEmpty()) return Optional.empty();
        requestTokens(context, 0, gain);
        PaymentRules.expect(context, "Propagation", payment.get(), PaymentRules.TECHNOLOGY_DONE);
        return Optional.of(
                AiDecision.press(card, button.get(), "follow Technology to gain command tokens with Propagation"));
    }

    private static Optional<AiDecision> requestResearch(AiTurnContext context, AiPrompt card, int cost, String reason) {
        Optional<PromptButton> button = card.enabledHandler(RESEARCH_BUTTON);
        if (button.isEmpty()) return Optional.empty();
        context.memory().put(requestKey(context, RESEARCH_KEY), context.now() + "|" + cost);
        return Optional.of(AiDecision.press(card, button.get(), reason));
    }

    private static boolean worthPayingForResearch(AiTurnContext context, int cost, double worth) {
        Game game = context.game();
        Player seat = context.seat();
        return worthPayingForResearch(game, seat, cost, worth, Wallet.of(game, seat));
    }

    static boolean worthPayingForResearch(Game game, Player seat, int cost, double worth, Wallet wallet) {
        if (ScoringReserve.planAfterReserve(wallet, ScoringReserve.of(game, seat), SpendCost.resources(cost))
                .isEmpty()) return false;
        return worthResearching(game, seat, worth);
    }

    static boolean worthResearching(Game game, Player seat, double worth) {
        return ResearchPolicy.bestResearchable(game, seat)
                .map(alias -> ResearchPolicy.value(game, seat, alias) >= worth)
                .orElse(false);
    }

    private static int propagationGain(AiTurnContext context) {
        return propagationGain(context.game(), context.seat());
    }

    private static int propagationGain(Game game, Player seat) {
        return Math.min(PROPAGATION_TOKENS, reinforcements(game, seat) + 1);
    }

    static boolean propagationWorthFollowing(Game game, Player seat) {
        return propagationGain(game, seat) >= MIN_PROPAGATION_FOLLOW_GAIN;
    }

    private static boolean hasRoomForSecret(Player seat) {
        return seat.getSoScored() + seat.getSecretsUnscored().size() < seat.getMaxSOCount();
    }

    private static boolean hasSpareStrategyToken(AiTurnContext context, StrategyCard card) {
        Player seat = context.seat();
        int saved = tokensSavedForBetterCards(context, card);
        int reservedTokens = ScoringReserve.of(context.game(), seat).tokens();
        return seat.getStrategicCC() - saved >= 1
                && seat.getStrategicCC() + seat.getTacticalCC() - 1 - saved >= reservedTokens;
    }

    static boolean cannotResearch(Player seat) {
        return seat.hasAbility("propagation");
    }

    private static Optional<long[]> request(AiTurnContext context, String prefix) {
        Optional<String> value = context.memory().get(requestKey(context, prefix));
        if (value.isEmpty()) return Optional.empty();
        String[] parts = value.get().split("\\|");
        if (parts.length != 2 || !StringUtils.isNumeric(parts[0]) || !StringUtils.isNumeric(parts[1])) {
            return Optional.empty();
        }
        long since = Long.parseLong(parts[0]);
        if (context.now() - since > REQUEST_WINDOW_MILLIS) {
            context.memory().remove(requestKey(context, prefix));
            return Optional.empty();
        }
        return Optional.of(new long[] {since, Long.parseLong(parts[1])});
    }

    private static String requestKey(AiTurnContext context, String prefix) {
        return prefix + context.game().getRound();
    }
}
