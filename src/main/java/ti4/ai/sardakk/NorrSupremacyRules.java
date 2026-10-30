package ti4.ai.sardakk;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.NekroRules;
import ti4.ai.perception.AiPrompt;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NorrSupremacyRules {

    private static final String BREAKTHROUGH = "sardakkbt";
    private static final String RESOLVE = "sardakkbtRes";
    private static final String GAIN_TOKEN = "gain_CC_";
    private static final String RESEARCH_UPGRADE = "acquireAUnitUpgrade_";
    private static final String NEITHER = "deleteButtons";
    private static final String GAIN_TOKENS_TEXT = "Use buttons to gain command tokens";
    private static final String FLOW_KEY = "norrSupremacy|";
    private static final String CLAIMED_KEY = "norrSupremacyClaimed|";
    private static final String ROUND_TRACKER = "combatRoundTracker";
    private static final String FIELD = "|";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final int FREE_RESEARCH_COST = 0;
    private static final int TOKENS_GAINED = 1;
    static final int SHORT_ON_TOKENS = 1;
    static final double SHORT_ON_TOKENS_PREMIUM = 2.0;

    enum Stage {
        RESOLVING,
        GAINING
    }

    enum Choice {
        RESEARCH,
        TOKEN,
        NEITHER
    }

    record Flow(Stage stage, long since) {

        String encode() {
            return stage.name() + FIELD + since;
        }

        static Optional<Flow> decode(String encoded) {
            String[] fields = StringUtils.split(encoded, FIELD);
            if (fields == null || fields.length != 2 || !StringUtils.isNumeric(fields[1])) return Optional.empty();
            try {
                return Optional.of(new Flow(Stage.valueOf(fields[0]), Long.parseLong(fields[1])));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasUnlockedBreakthrough(BREAKTHROUGH)) return Optional.empty();
        Optional<Flow> flow = current(context);
        if (flow.isPresent()) return followUp(context, flow.get());
        return claim(context);
    }

    static Choice choose(Game game, Player seat, boolean researchOffered, boolean tokenOffered) {
        double research = researchOffered ? bestUpgradeWorth(game, seat) : 0;
        double token = tokenOffered ? tokenWorth(game, seat) : 0;
        if (research > 0 && research >= token) return Choice.RESEARCH;
        return token > 0 ? Choice.TOKEN : Choice.NEITHER;
    }

    static double bestUpgradeWorth(Game game, Player seat) {
        if (StrategyCardRules.cannotResearch(seat)) return 0;
        List<String> researchable = researchableUpgrades(game, seat);
        return ResearchPolicy.best(game, seat, researchable)
                .map(alias -> ResearchPolicy.TECH_TEMPO * ResearchPolicy.researchValue(game, seat, alias)
                        + ScoringStakes.scoreValue(
                                game, seat, ResearchPolicy.publicPointsCompletedBy(game, seat, alias)))
                .orElse(0.0);
    }

    static double tokenWorth(Game game, Player seat) {
        double worth = CommandTokenValue.tactic(game, seat, TOKENS_GAINED);
        return shortOnTokens(seat) ? worth * SHORT_ON_TOKENS_PREMIUM : worth;
    }

    private static boolean shortOnTokens(Player seat) {
        return seat.getTacticalCC() + seat.getStrategicCC() <= SHORT_ON_TOKENS;
    }

    private static List<String> researchableUpgrades(Game game, Player seat) {
        return ListTechService.getAllTechOfAType(game, TechnologyType.UNITUPGRADE.toString(), seat, false, true)
                .stream()
                .map(TechnologyModel::getAlias)
                .toList();
    }

    private static Optional<AiDecision> claim(AiTurnContext context) {
        Optional<Match> reminder = Prompts.owned(Prompts.thisTurn(context), context.faction(), RESOLVE::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (reminder.isEmpty()) return Optional.empty();
        Optional<String> battlefield = unclaimedWin(context);
        if (battlefield.isEmpty()) return Optional.empty();
        context.memory().put(CLAIMED_KEY + context.turnKey() + FIELD + battlefield.get(), "claimed");
        remember(context, new Flow(Stage.RESOLVING, context.now()));
        return Optional.of(reminder.get().press("resolve N'orr Supremacy after winning a combat"));
    }

    private static Optional<String> unclaimedWin(AiTurnContext context) {
        Game game = context.game();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) return Optional.empty();
        return tile.getUnitHolders().values().stream()
                .filter(holder -> wonHere(context, tile, holder))
                .map(holder -> tile.getPosition() + FIELD + holder.getName())
                .filter(battlefield -> !context.memory().has(CLAIMED_KEY + context.turnKey() + FIELD + battlefield))
                .findFirst();
    }

    private static boolean wonHere(AiTurnContext context, Tile tile, UnitHolder holder) {
        Game game = context.game();
        Player seat = context.seat();
        boolean space = BoardView.SPACE.equals(holder.getName());
        List<Player> others =
                game.getRealPlayers().stream().filter(other -> other != seat).toList();
        boolean fought = rounds(game, seat, tile, holder)
                        + others.stream()
                                .mapToInt(other -> rounds(game, other, tile, holder))
                                .sum()
                > 0;
        return fought
                && present(seat, tile, holder, space)
                && others.stream().noneMatch(other -> present(other, tile, holder, space));
    }

    private static boolean present(Player player, Tile tile, UnitHolder holder, boolean space) {
        return space ? BoardView.hasOwnShips(player, tile) : BoardView.groundForces(holder, player) > 0;
    }

    private static int rounds(Game game, Player player, Tile tile, UnitHolder holder) {
        String value = game.getStoredValue(ROUND_TRACKER + player.getFaction() + tile.getPosition() + holder.getName());
        return StringUtils.isNumeric(value) ? Integer.parseInt(value) : 0;
    }

    private static Optional<AiDecision> followUp(AiTurnContext context, Flow flow) {
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= flow.since() - CLOCK_SKEW_MILLIS)
                .toList();
        Optional<AiDecision> step =
                switch (flow.stage()) {
                    case RESOLVING -> answerChoice(context, fresh);
                    case GAINING -> gainToken(context, fresh);
                };
        if (step.isPresent()) return step;
        long until = flow.since() + STEP_WAIT_MILLIS;
        if (context.now() < until) return Optional.of(new AiDecision.Wait(until, "N'orr Supremacy"));
        context.memory().remove(FLOW_KEY + context.turnKey());
        return Optional.empty();
    }

    private static Optional<AiDecision> answerChoice(AiTurnContext context, List<AiPrompt> fresh) {
        Optional<Match> research = Prompts.unowned(fresh, RESEARCH_UPGRADE::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        Optional<Match> token = Prompts.unowned(fresh, GAIN_TOKEN::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        Optional<Match> neither = Prompts.unowned(fresh, NEITHER::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (research.isEmpty() && token.isEmpty()) return Optional.empty();
        Choice choice = choose(context.game(), context.seat(), research.isPresent(), token.isPresent());
        return switch (choice) {
            case RESEARCH -> {
                StrategyCardRules.expectResearch(context, FREE_RESEARCH_COST);
                context.memory().remove(FLOW_KEY + context.turnKey());
                yield research.map(match -> match.press("research the best unit upgrade with N'orr Supremacy"));
            }
            case TOKEN -> {
                remember(context, new Flow(Stage.GAINING, context.now()));
                yield token.map(match -> match.press("gain a command token with N'orr Supremacy"));
            }
            case NEITHER -> {
                context.memory().remove(FLOW_KEY + context.turnKey());
                yield neither.map(match -> match.press("take neither N'orr Supremacy reward"));
            }
        };
    }

    private static Optional<AiDecision> gainToken(AiTurnContext context, List<AiPrompt> fresh) {
        int target =
                Math.min(TOKENS_GAINED, CommandTokenPolicy.gainWithinReinforcements(context.game(), context.seat()));
        for (AiPrompt prompt : fresh) {
            if (!prompt.content().contains(GAIN_TOKENS_TEXT) || !Prompts.mentions(prompt, context.seat())) continue;
            Optional<AiDecision> gain =
                    NekroRules.gainTokens(context, prompt, target, FLOW_KEY + context.turnKey(), "N'orr Supremacy");
            if (gain.isPresent()) {
                if (finishes(gain.get())) context.memory().remove(FLOW_KEY + context.turnKey());
                return gain;
            }
        }
        return Optional.empty();
    }

    private static boolean finishes(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                && NEITHER.equals(press.button().handlerId());
    }

    private static Optional<Flow> current(AiTurnContext context) {
        return context.memory().get(FLOW_KEY + context.turnKey()).flatMap(Flow::decode);
    }

    private static void remember(AiTurnContext context, Flow flow) {
        context.memory().put(FLOW_KEY + context.turnKey(), flow.encode());
    }
}
