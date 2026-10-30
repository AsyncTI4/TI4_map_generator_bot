package ti4.ai.hacan;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.brain.AgentPick;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.Stall;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.trade.TradeRules;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class CarthAgentRules {

    private static final String AGENT = "hacanagent";
    private static final String USE_AGENT = "exhaustAgent_hacanagent";
    private static final String AGENT_NAME = "Carth of Golden Sands";
    private static final String KEY = "hacanAgent|";
    private static final String DONE = "done";
    private static final int COMMODITIES = 2;
    private static final long STEP_WAIT_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static Optional<AiDecision> start(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasLeader(AGENT) || !seat.hasUnexhaustedLeader(AGENT) || !onOwnTurn(context)) return Optional.empty();
        if (context.memory().has(KEY + context.turnKey()) || TradeRules.humanDealPending(context)) {
            return Optional.empty();
        }
        Optional<Player> other = otherTarget(context);
        if (other.isEmpty() && !worthGaining(game, seat)) return Optional.empty();
        if (other.isPresent() && !AiSeats.isAiSeat(other.get())) {
            return TradeRules.carthProposal(context, other.get());
        }
        Optional<Match> use = Prompts.owned(Prompts.thisTurn(context), context.faction(), USE_AGENT::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (use.isEmpty()) return Optional.empty();
        context.memory().put(KEY + context.turnKey(), String.valueOf(context.now()));
        other.ifPresent(target -> TradeRules.rememberCarthTarget(context, target));
        String reason = other.isPresent()
                ? "use Carth of Golden Sands to replenish a partner who trades the commodities to me"
                : "use Carth of Golden Sands to gain 2 commodities to trade away";
        return Optional.of(use.get().press(reason));
    }

    public static Optional<AiDecision> chooseTarget(AiTurnContext context) {
        String key = KEY + context.turnKey();
        Optional<Long> since =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Long::parseLong);
        if (since.isEmpty()) return Optional.empty();
        Player seat = context.seat();
        Player chosen = TradeRules.rememberedCarthTarget(context).orElse(seat);
        List<String> wanted = List.of(chosen.getFaction(), chosen.getColor());
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.createdAtMillis() < since.get() - CLOCK_SKEW_MILLIS) continue;
            if (!prompt.content().contains(AGENT_NAME)) continue;
            Optional<PromptButton> target = AgentPick.target(prompt, context.faction(), AGENT, wanted);
            if (target.isPresent() && !context.alreadyPressed(prompt, target.get())) {
                context.memory().put(key, DONE);
                return Optional.of(AiDecision.press(prompt, target.get(), reason(chosen, seat)));
            }
        }
        if (context.now() < since.get() + STEP_WAIT_MILLIS) {
            return Optional.of(new AiDecision.Wait(since.get() + STEP_WAIT_MILLIS, "Carth of Golden Sands' target"));
        }
        context.memory().put(key, DONE);
        return Optional.empty();
    }

    private static Optional<Player> otherTarget(AiTurnContext context) {
        if (tradeStillToReplenish(context.game())) return Optional.empty();
        return TradeRules.carthTarget(context);
    }

    private static String reason(Player chosen, Player seat) {
        return chosen == seat
                ? "gain 2 commodities with Carth of Golden Sands"
                : "replenish " + chosen.getFaction() + " with Carth of Golden Sands";
    }

    static boolean worthGaining(Game game, Player seat) {
        int room = seat.getCommoditiesTotal() - seat.getCommodities();
        if (room <= 0 || tradeStillToReplenish(game)) return false;
        return room >= COMMODITIES || !Stall.othersStillActing(game, seat);
    }

    private static boolean tradeStillToReplenish(Game game) {
        return game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.TRADE
                        && !game.getPlayedSCs().contains(initiative));
    }

    private static boolean onOwnTurn(AiTurnContext context) {
        return context.isActivePlayer()
                && "action".equalsIgnoreCase(context.game().getPhaseOfGame())
                && !TacticalRules.inProgress(context.game(), context.seat());
    }
}
