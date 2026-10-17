package ti4.ai.nekro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.secrets.SpyNetwork;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NekroLeaderRules {

    private static final String AGENT = "nekroagent";
    private static final String HERO = "nekrohero";
    private static final String AGENT_NAME = "Nekro Malleon";
    private static final String USE_AGENT = "exhaustAgent_nekroagent";
    private static final String AGENT_TARGET_PREFIX = "nekroAgentRes_";
    private static final String AGENT_KEY = "nekroAgent|";
    private static final String AGENT_DONE = "done";
    private static final String COST_KEY = "nekroAgentCost|";
    private static final String COST_PAID = "paid";
    private static final String DISCARD_PROMPT_ENDING = " use buttons to discard";
    private static final String LOSE_TOKENS_TEXT = "Use buttons to lose tokens.";
    private static final String DONE_LOSING = "Done Losing";
    private static final String DECREASE_PREFIX = "decrease_";
    private static final String AC_DISCARD = "ac_discard_from_hand_";
    private static final String HERO_KEY = "nekroHero|";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String HERO_BUTTON = "componentActionRes_leader_nekrohero";
    private static final String HERO_PLANET_PREFIX = "nekroHeroStep2_";
    private static final String GET_TECH = "getTech_";
    private static final String NO_PAY = "__noPay";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final double TRADE_GOOD_VALUE = 0.5;
    private static final double DESTROYED_UNIT_VALUE = 1.0;
    private static final double HERO_MIN_VALUE = 3.0;

    enum HeroStage {
        MENU,
        LEADER,
        PLANET,
        DONE
    }

    record HeroStep(HeroStage stage, long since, String planet) {

        String encode() {
            return String.join(FIELD, stage.name(), String.valueOf(since), planet);
        }

        static Optional<HeroStep> decode(String encoded) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != 3 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            try {
                return Optional.of(new HeroStep(HeroStage.valueOf(fields[0]), Long.parseLong(fields[1]), fields[2]));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    record HeroTarget(String planet, double value) {}

    public static Optional<AiDecision> startAgent(AiTurnContext context) {
        Player seat = context.seat();
        if (!ownsReadyAgent(seat) || !onOwnTurn(context) || !canSpareActionCard(context.game(), seat)) {
            return Optional.empty();
        }
        if (TacticalRules.actionTaken(context)) return Optional.empty();
        if (context.memory().has(AGENT_KEY + context.turnKey())) return Optional.empty();
        Optional<Match> use = Prompts.owned(Prompts.thisTurn(context), context.faction(), USE_AGENT::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (use.isEmpty()) return Optional.empty();
        context.memory().put(AGENT_KEY + context.turnKey(), String.valueOf(context.now()));
        return Optional.of(use.get().press("use Nekro Malleon to trade an action card for 2 trade goods"));
    }

    public static Optional<AiDecision> chooseAgentTarget(AiTurnContext context) {
        String key = AGENT_KEY + context.turnKey();
        Optional<Long> since =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Long::parseLong);
        if (since.isEmpty()) return Optional.empty();
        String self = AGENT_TARGET_PREFIX + context.faction();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since.get() - CLOCK_SKEW_MILLIS) continue;
            if (!prompt.content().contains(AGENT_NAME) || !NekroRules.mentions(prompt, context.seat())) continue;
            Optional<PromptButton> target =
                    prompt.firstEnabled(button -> button.isUnowned() && self.equals(button.handlerId()));
            if (target.isPresent() && !context.alreadyPressed(prompt, target.get())) {
                context.memory().put(key, AGENT_DONE);
                return Optional.of(AiDecision.press(prompt, target.get(), "take Nekro Malleon's 2 trade goods itself"));
            }
        }
        if (context.now() < since.get() + STEP_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(since.get() + STEP_WAIT_MILLIS, "Nekro Malleon's target"));
        }
        context.memory().put(key, AGENT_DONE);
        return Optional.empty();
    }

    public static Optional<AiDecision> payAgentCost(AiTurnContext context) {
        Player seat = context.seat();
        for (AiPrompt tokens : Prompts.newestFirst(context.prompts())) {
            if (tokens.isHidden() || !losesTokens(context, tokens)) continue;
            String key = COST_KEY + tokens.messageId();
            if (!context.memory().has(key)) context.memory().put(key, String.valueOf(seat.getAcCount()));
            String state = context.memory().get(key).orElse("");
            boolean paid = COST_PAID.equals(state)
                    || (StringUtils.isNumeric(state) && seat.getAcCount() < Integer.parseInt(state))
                    || CommandTokenPolicy.netGainSoFar(context.game(), seat) < 0;
            if (!paid) {
                Optional<AiDecision> payment = pay(context, tokens);
                if (payment.isPresent()) {
                    if (payment.get() instanceof AiDecision.Press)
                        context.memory().put(key, COST_PAID);
                    return payment;
                }
            }
            Optional<PromptButton> done = tokens.firstEnabled(button -> button.isOwnedBy(context.faction())
                    && "deleteButtons".equals(button.handlerId())
                    && button.label().startsWith(DONE_LOSING));
            if (done.isPresent() && !context.alreadyPressed(tokens, done.get())) {
                context.memory().remove(key);
                return Optional.of(AiDecision.press(tokens, done.get(), "finish paying for Nekro Malleon"));
            }
        }
        return Optional.empty();
    }

    private static boolean losesTokens(AiTurnContext context, AiPrompt prompt) {
        boolean ownDecrease = prompt.buttons().stream()
                .anyMatch(button -> button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith(DECREASE_PREFIX));
        if (!ownDecrease) return false;
        return prompt.content().contains(LOSE_TOKENS_TEXT) || context.memory().has(COST_KEY + prompt.messageId());
    }

    private static Optional<AiDecision> pay(AiTurnContext context, AiPrompt tokens) {
        Player seat = context.seat();
        if (canSpareActionCard(context.game(), seat)) {
            Optional<AiDecision> discard = discardFor(context, tokens);
            if (discard.isPresent()) return discard;
            long until = tokens.createdAtMillis() + STEP_WAIT_MILLIS;
            if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "Nekro Malleon's discard prompt"));
        }
        for (String pool : CommandTokenPolicy.poolsToShrink(context.game(), seat)) {
            Optional<PromptButton> decrease = tokens.firstEnabled(
                    button -> button.isOwnedBy(context.faction()) && pool.equals(button.handlerId()));
            if (decrease.isPresent() && !context.alreadyPressed(tokens, decrease.get())) {
                return Optional.of(AiDecision.press(tokens, decrease.get(), "spend a command token for Nekro Malleon"));
            }
        }
        return seat.getAcCount() > 0 ? discardFor(context, tokens) : Optional.empty();
    }

    private static Optional<AiDecision> discardFor(AiTurnContext context, AiPrompt tokens) {
        long since = tokens.createdAtMillis() - CLOCK_SKEW_MILLIS;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            if (!prompt.content().endsWith(DISCARD_PROMPT_ENDING)) continue;
            Optional<PromptButton> discard = ActionCardValue.worstDiscard(
                    context.game(),
                    context.seat(),
                    prompt,
                    AC_DISCARD,
                    "",
                    button -> button.isUnowned() && !context.alreadyPressed(prompt, button));
            if (discard.isPresent()) {
                return Optional.of(AiDecision.press(prompt, discard.get(), "discard an action card for Nekro Malleon"));
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> startHero(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasLeaderUnlocked(HERO) || !onOwnTurn(context) || TacticalRules.actionTaken(context)) {
            return Optional.empty();
        }
        if (context.memory().has(HERO_KEY + context.turnKey())) return Optional.empty();
        List<AiPrompt> turn = Prompts.thisTurn(context);
        String faction = context.faction();
        if (Prompts.owned(turn, faction, id -> "turnEnd".equals(id) || "endOfTurnAbilities".equals(id))
                .isPresent()) {
            return Optional.empty();
        }
        Optional<Match> component = Prompts.owned(turn, faction, COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (component.isEmpty()) return Optional.empty();
        Optional<HeroTarget> target = bestHeroTarget(context.game(), seat, List.of());
        if (target.isEmpty() || target.get().value() < HERO_MIN_VALUE) return Optional.empty();
        HeroStep step = new HeroStep(HeroStage.MENU, context.now(), target.get().planet());
        context.memory().put(HERO_KEY + context.turnKey(), step.encode());
        return Optional.of(component
                .get()
                .press("play UNIT.DSGN.FLAYESH on " + target.get().planet()));
    }

    public static Optional<AiDecision> continueHero(AiTurnContext context) {
        String key = HERO_KEY + context.turnKey();
        Optional<HeroStep> step = context.memory().get(key).flatMap(HeroStep::decode);
        if (step.isEmpty() || step.get().stage() == HeroStage.DONE) return Optional.empty();
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.isHidden()
                        && prompt.createdAtMillis() >= step.get().since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> next =
                switch (step.get().stage()) {
                    case MENU -> playHero(context, fresh, step.get());
                    case LEADER -> choosePlanet(context, fresh, step.get());
                    case PLANET -> chooseTechnology(context, fresh, step.get());
                    case DONE -> Optional.empty();
                };
        if (next.isPresent()) return next;
        long until = step.get().since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "UNIT.DSGN.FLAYESH"));
        context.memory()
                .put(key, new HeroStep(HeroStage.DONE, context.now(), step.get().planet()).encode());
        return Optional.empty();
    }

    private static Optional<AiDecision> playHero(AiTurnContext context, List<AiPrompt> fresh, HeroStep step) {
        Optional<Match> hero = Prompts.owned(fresh, context.faction(), HERO_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (hero.isEmpty()) return Optional.empty();
        remember(context, new HeroStep(HeroStage.LEADER, context.now(), step.planet()));
        return Optional.of(hero.get().press("play UNIT.DSGN.FLAYESH"));
    }

    private static Optional<AiDecision> choosePlanet(AiTurnContext context, List<AiPrompt> fresh, HeroStep step) {
        List<Match> offered = new ArrayList<>();
        for (AiPrompt prompt : fresh) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isUnowned() && button.handlerId().startsWith(HERO_PLANET_PREFIX)) {
                    offered.add(new Match(prompt, button));
                }
            }
        }
        if (offered.isEmpty()) return Optional.empty();
        List<String> planets = offered.stream()
                .map(match -> StringUtils.removeStart(match.button().handlerId(), HERO_PLANET_PREFIX))
                .toList();
        String planet = planets.contains(step.planet())
                ? step.planet()
                : bestHeroTarget(context.game(), context.seat(), planets)
                        .map(HeroTarget::planet)
                        .orElse(planets.getFirst());
        Match choice = offered.get(planets.indexOf(planet));
        remember(context, new HeroStep(HeroStage.PLANET, context.now(), planet));
        return Optional.of(choice.press("scour " + planet + " with UNIT.DSGN.FLAYESH"));
    }

    private static Optional<AiDecision> chooseTechnology(AiTurnContext context, List<AiPrompt> fresh, HeroStep step) {
        List<Match> offered = new ArrayList<>();
        for (AiPrompt prompt : fresh) {
            for (PromptButton button : prompt.enabledButtons()) {
                String id = button.handlerId();
                if (button.isOwnedBy(context.faction()) && id.startsWith(GET_TECH) && id.endsWith(NO_PAY)) {
                    offered.add(new Match(prompt, button));
                }
            }
        }
        if (offered.isEmpty()) return Optional.empty();
        List<String> aliases =
                offered.stream().map(match -> technologyOf(match.button())).toList();
        Optional<String> best = CopiedTechPolicy.best(context.game(), context.seat(), aliases);
        Match choice = offered.get(aliases.indexOf(best.orElse(aliases.getFirst())));
        remember(context, new HeroStep(HeroStage.DONE, context.now(), step.planet()));
        return Optional.of(choice.press("gain " + technologyOf(choice.button()) + " with UNIT.DSGN.FLAYESH"));
    }

    private static String technologyOf(PromptButton button) {
        return StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), GET_TECH), NO_PAY);
    }

    private static void remember(AiTurnContext context, HeroStep step) {
        context.memory().put(HERO_KEY + context.turnKey(), step.encode());
    }

    static Optional<HeroTarget> bestHeroTarget(Game game, Player seat, List<String> onlyThese) {
        List<HeroTarget> targets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.containsPlayersUnits(seat)) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String name = planet.getName();
                if (!onlyThese.isEmpty() && !onlyThese.contains(name)) continue;
                if (!ButtonHelper.checkForTechSkips(game, name)) continue;
                targets.add(new HeroTarget(name, heroValue(game, seat, planet)));
            }
        }
        return targets.stream().max(Comparator.comparingDouble(HeroTarget::value));
    }

    private static double heroValue(Game game, Player seat, Planet planet) {
        double tradeGoods = TRADE_GOOD_VALUE * (planet.getResources() + planet.getInfluence());
        double technology = 0;
        for (String speciality : planet.getTechSpecialities()) {
            for (TechnologyModel tech : ListTechService.getAllTechOfAType(game, speciality, seat)) {
                technology = Math.max(technology, CopiedTechPolicy.value(game, seat, tech.getAlias()));
            }
        }
        return tradeGoods + technology + DESTROYED_UNIT_VALUE * enemyUnits(game, seat, planet);
    }

    private static int enemyUnits(Game game, Player seat, Planet planet) {
        int units = 0;
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (UnitKey key : planet.getUnitKeysForPlayer(other)) units += planet.getUnitCount(key);
        }
        return units;
    }

    private static boolean ownsReadyAgent(Player seat) {
        return seat.hasLeader(AGENT) && seat.hasUnexhaustedLeader(AGENT);
    }

    private static boolean onOwnTurn(AiTurnContext context) {
        return context.isActivePlayer()
                && "action".equalsIgnoreCase(context.game().getPhaseOfGame())
                && !TacticalRules.inProgress(context.game(), context.seat());
    }

    static boolean canSpareActionCard(Game game, Player seat) {
        int cards = seat.getAcCount();
        if (cards <= 0) return false;
        if (SpyNetwork.holds(seat) && cards <= SpyNetwork.CARDS) return false;
        return ActionCardValue.hasSpareCard(game, seat);
    }
}
