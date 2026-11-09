package ti4.ai.winnu;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class MathisMathinusRules {

    public static final String IMPERIAL_POINT = "score_imperial";
    static final String HERO = "winnuhero";
    static final int DECISIVE_POINTS = 2;
    static final String HERO_BUTTON = "componentActionRes_leader_" + HERO;
    static final String PICK_PREFIX = "winnuHero_";
    static final String ALLOW_FOLLOW = "winnuHeroAllowFollow_";
    private static final String COMPONENT_ACTION = "componentAction";
    private static final String DONE_CHOOSING = "deleteButtons";
    private static final String KEY = "mathisMathinus|";
    private static final String MENU = "menu";
    private static final String LEADER = "leader";
    private static final String PICKED = "picked";
    private static final String DONE = "done";
    private static final String FIELD = "~";
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context)) return Optional.empty();
        int points = imperialPoints(context.game(), context.seat());
        if (points < DECISIVE_POINTS && !wins(context.game(), context.seat(), points)) return Optional.empty();
        return open(context, thisTurn, "to score " + points + " points with Imperial now");
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!canStart(context) || imperialPoints(context.game(), context.seat()) < 1) return Optional.empty();
        return open(context, thisTurn, "to score with Imperial instead of passing");
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Optional<String[]> state = state(context);
        if (state.isEmpty()) return Optional.empty();
        long since = Long.parseLong(state.get()[1]);
        List<AiPrompt> fresh = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= since - CLOCK_SKEW_MILLIS)
                .toList();
        return switch (state.get()[0]) {
            case MENU -> playHero(context, fresh, since);
            case LEADER -> pickImperial(context, fresh, since);
            case PICKED -> chooseNoFollowers(context, fresh);
            default -> Optional.empty();
        };
    }

    private static Optional<AiDecision> playHero(AiTurnContext context, List<AiPrompt> fresh, long since) {
        Optional<Match> hero = Prompts.owned(fresh, context.faction(), HERO_BUTTON::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (hero.isPresent()) {
            remember(context, LEADER, context.now());
            return Optional.of(hero.get().press("play Mathis Mathinus for the primary of Imperial"));
        }
        return waitOrGiveUp(context, since);
    }

    private static Optional<AiDecision> pickImperial(AiTurnContext context, List<AiPrompt> fresh, long since) {
        Optional<Integer> imperial = imperialInitiative(context.game());
        Optional<Match> pick = imperial.flatMap(initiative -> Prompts.unowned(fresh, (PICK_PREFIX + initiative)::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button())));
        if (pick.isPresent()) {
            remember(context, PICKED, context.now());
            StrategyCardRules.beginOverruledPrimary(context, imperial.get());
            return Optional.of(pick.get().press("perform the primary of Imperial with Mathis Mathinus"));
        }
        return waitOrGiveUp(context, since);
    }

    private static Optional<AiDecision> chooseNoFollowers(AiTurnContext context, List<AiPrompt> fresh) {
        for (AiPrompt prompt : fresh) {
            boolean permissions = prompt.enabledButtons().stream()
                    .anyMatch(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(ALLOW_FOLLOW));
            if (!permissions) continue;
            Optional<PromptButton> done =
                    prompt.enabledHandler(DONE_CHOOSING).filter(button -> !context.alreadyPressed(prompt, button));
            if (done.isEmpty()) continue;
            remember(context, DONE, context.now());
            return Optional.of(
                    AiDecision.press(prompt, done.get(), "let no other player perform the secondary of Imperial"));
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> waitOrGiveUp(AiTurnContext context, long since) {
        if (context.now() >= since + STEP_WAIT_MILLIS) {
            remember(context, DONE, context.now());
            return Optional.empty();
        }
        return Optional.of(new AiDecision.Wait(since + STEP_WAIT_MILLIS, "Mathis Mathinus"));
    }

    static int imperialPoints(Game game, Player seat) {
        int mecatol = seat.controlsMecatol(true) ? 1 : 0;
        int scorable = ObjectivePolicy.scorablePublics(game, seat).size();
        int extraPublic = scorable > ScoringReserve.scoringChances(game, seat) ? 1 : 0;
        return mecatol + extraPublic;
    }

    private static boolean wins(Game game, Player seat, int points) {
        return points > 0 && game.getVp() > 0 && seat.getTotalVictoryPoints() + points >= game.getVp();
    }

    static Optional<Integer> imperialInitiative(Game game) {
        return game.getSCList().stream()
                .filter(initiative -> initiative > 0 && StrategyCard.of(game, initiative) == StrategyCard.IMPERIAL)
                .findFirst();
    }

    private static boolean canStart(AiTurnContext context) {
        Game game = context.game();
        return context.seat().hasLeaderUnlocked(HERO)
                && context.isActivePlayer()
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && !TacticalRules.inProgress(game, context.seat())
                && !TacticalRules.actionTaken(context)
                && imperialInitiative(game).isPresent()
                && state(context).isEmpty();
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String why) {
        Optional<Match> menu = Prompts.owned(thisTurn, context.faction(), COMPONENT_ACTION::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (menu.isEmpty()) return Optional.empty();
        remember(context, MENU, context.now());
        return Optional.of(menu.get().press("play Mathis Mathinus " + why));
    }

    private static Optional<String[]> state(AiTurnContext context) {
        return context.memory()
                .get(KEY + context.turnKey())
                .map(value -> StringUtils.splitPreserveAllTokens(value, FIELD))
                .filter(fields -> fields.length == 2 && StringUtils.isNumeric(fields[1]));
    }

    private static void remember(AiTurnContext context, String stage, long since) {
        context.memory().put(KEY + context.turnKey(), stage + FIELD + since);
    }
}
