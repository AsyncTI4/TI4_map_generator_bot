package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

@UtilityClass
public class SabotageRules {

    private static final String NO_SABOTAGE = "no_sabotage";
    private static final String BUTTON_KEY = "saboBtn|";
    private static final String FOLLOW_KEY = "saboFollowUp|";
    private static final String CANCEL_LABEL = "Cancel Action Card With Sabotage";
    private static final String CRISIS_SABOTAGED = "crisisSabotaged_";
    private static final String STRATEGIC_ACTION = "strategicAction_";
    private static final String COUPED_TEXT = "have been Coup'd";
    private static final String COUP = "Coup d'Etat";
    private static final String CRISIS = "Crisis";
    private static final long MIN_SELF_PLAY_PASS_DELAY_SECONDS = 5;
    private static final int SELF_PLAY_PASS_DELAY_SPREAD_SECONDS = 10;
    private static final long MIN_PASS_DELAY_MINUTES = 10;
    private static final long PASS_DELAY_SPREAD_MINUTES = 30;
    private static final long HOLD_MILLIS = 60_000L;
    private static final long HOLD_CEILING_MILLIS = TimeUnit.DAYS.toMillis(1);

    public static Optional<AiDecision> answerWindowsAimedAtSeat(AiTurnContext context) {
        for (AiPrompt window : windows(context)) {
            if (!SabotagePolicy.aimedAtSeat(context, window)) continue;
            Optional<AiDecision> answer = answer(context, window);
            if (answer.isPresent()) return answer;
            if (stillOpenAfterAnswering(context, window)) {
                return Optional.of(new AiDecision.Wait(context.now() + HOLD_MILLIS, "a Sabotage window aimed at it"));
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> answerWindows(AiTurnContext context) {
        for (AiPrompt window : windows(context)) {
            Optional<AiDecision> answer = answer(context, window);
            if (answer.isPresent()) return answer;
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> followUp(AiTurnContext context) {
        if (!context.isActivePlayer() || AiSettings.usesLegacyActionCards(context.seat())) return Optional.empty();
        return crisisSabotaged(context).or(() -> couped(context));
    }

    private static Optional<AiDecision> answer(AiTurnContext context, AiPrompt window) {
        Optional<PromptButton> pass = window.enabledHandler(NO_SABOTAGE);
        if (pass.isEmpty() || context.alreadyPressed(window, pass.get()) || windowClosedFor(context, window)) {
            return Optional.empty();
        }
        long readyAt = window.createdAtMillis() + passDelayMillis(context, window);
        if (context.now() < readyAt) return Optional.of(new AiDecision.Wait(readyAt, "sabotage window"));
        Optional<SabotagePolicy.Choice> cancel = cancelChoice(context, window);
        if (cancel.isPresent()) {
            SabotagePolicy.Choice choice = cancel.get();
            if (choice.instinctTraining()) InstinctTraining.remember(context, window);
            return Optional.of(AiDecision.press(choice.prompt(), choice.button(), choice.reason()));
        }
        return Optional.of(AiDecision.press(window, pass.get(), "no sabotage"));
    }

    private static Optional<SabotagePolicy.Choice> cancelChoice(AiTurnContext context, AiPrompt window) {
        if (InstinctTraining.spentOn(context, window)) return Optional.empty();
        return SabotagePolicy.choose(context, window)
                .filter(choice -> !context.alreadyPressed(choice.prompt(), choice.button()));
    }

    private static List<AiPrompt> windows(AiTurnContext context) {
        List<AiPrompt> windows = new ArrayList<>();
        Set<String> visible = new HashSet<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            visible.add(prompt.messageId());
            if (prompt.enabledHandler(NO_SABOTAGE).isEmpty()) continue;
            rememberCancelButton(context, prompt);
            windows.add(prompt);
        }
        windows.addAll(scrolledOutWindows(context, visible));
        return windows;
    }

    private static void rememberCancelButton(AiTurnContext context, AiPrompt prompt) {
        prompt.firstEnabled(
                        button -> button.isUnowned() && button.handlerId().startsWith(SabotagePolicy.SABOTAGE_PREFIX))
                .ifPresent(button -> context.memory().put(BUTTON_KEY + prompt.messageId(), button.customId()));
    }

    private static List<AiPrompt> scrolledOutWindows(AiTurnContext context, Set<String> visible) {
        Game game = context.game();
        String channelId = game.getMainChannelID();
        if (channelId == null) return List.of();
        List<AiPrompt> windows = new ArrayList<>();
        for (GameMessage window : GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD)) {
            if (visible.contains(window.messageId()) || !StringUtils.isNumeric(window.messageId())) continue;
            if (window.factionsThatReacted().contains(context.faction())) continue;
            long created = TimeUtil.getTimeCreated(Long.parseLong(window.messageId()))
                    .toInstant()
                    .toEpochMilli();
            List<PromptButton> buttons = new ArrayList<>();
            buttons.add(new PromptButton(0, NO_SABOTAGE, NO_SABOTAGE, null, "No Sabotage", false));
            context.memory()
                    .get(BUTTON_KEY + window.messageId())
                    .ifPresent(customId ->
                            buttons.add(new PromptButton(1, customId, customId, null, CANCEL_LABEL, false)));
            windows.add(
                    new AiPrompt(channelId, window.messageId(), AiPrompt.PromptSource.PUBLIC, "", buttons, created));
        }
        return windows;
    }

    private static long passDelayMillis(AiTurnContext context, AiPrompt prompt) {
        int seed = prompt.messageId().hashCode() ^ (int) context.profile().seed();
        if (AiSeats.isSelfPlay(context.game())) {
            return TimeUnit.SECONDS.toMillis(
                    MIN_SELF_PLAY_PASS_DELAY_SECONDS + Math.floorMod(seed, SELF_PLAY_PASS_DELAY_SPREAD_SECONDS));
        }
        long spread =
                Math.floorMod(prompt.messageId().hashCode() ^ context.profile().seed(), PASS_DELAY_SPREAD_MINUTES);
        return TimeUnit.MINUTES.toMillis(MIN_PASS_DELAY_MINUTES + spread);
    }

    private static boolean windowClosedFor(AiTurnContext context, AiPrompt prompt) {
        return GameMessageManager.getOne(context.game().getName(), prompt.messageId())
                .map(GameMessage::factionsThatReacted)
                .map(factions -> factions.contains(context.faction()))
                .orElse(true);
    }

    private static boolean stillOpenAfterAnswering(AiTurnContext context, AiPrompt window) {
        if (context.now() > window.createdAtMillis() + HOLD_CEILING_MILLIS) return false;
        Optional<GameMessage> entry = GameMessageManager.getOne(context.game().getName(), window.messageId());
        if (entry.isEmpty()) return false;
        String cardPlayer = SabotagePolicy.cardPlayerFaction(window).orElse("");
        return context.game().getRealPlayers().stream()
                .map(Player::getFaction)
                .filter(faction -> !faction.equals(cardPlayer))
                .anyMatch(faction -> !entry.get().factionsThatReacted().contains(faction));
    }

    private static Optional<AiDecision> crisisSabotaged(AiTurnContext context) {
        String button = CRISIS_SABOTAGED + context.seat().getColor();
        if (!SabotageWindow.latestPlayCanceled(context.game(), CRISIS)) return Optional.empty();
        return Prompts.unowned(Prompts.thisTurn(context), button::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("Crisis was cancelled, so carry on with the turn"));
    }

    private static Optional<AiDecision> couped(AiTurnContext context) {
        String key = FOLLOW_KEY + context.turnKey();
        if (context.memory().has(key) || !SabotageWindow.latestPlayCanceled(context.game(), COUP)) {
            return Optional.empty();
        }
        boolean couped = Prompts.thisTurn(context).stream()
                .anyMatch(prompt -> !prompt.isHidden()
                        && prompt.content().contains(COUPED_TEXT)
                        && Prompts.mentions(prompt, context.seat()));
        if (!couped) return Optional.empty();
        Game game = context.game();
        Optional<Match> card = Prompts.owned(
                        Prompts.thisTurn(context), context.faction(), id -> id.startsWith(STRATEGIC_ACTION))
                .filter(match -> StrategyCardRanking.initiative(match.button()) > 0
                        && !game.getPlayedSCs().contains(StrategyCardRanking.initiative(match.button())));
        if (card.isEmpty()) return Optional.empty();
        context.memory().put(key, "done");
        return card.map(match -> StrategyCardRules.play(
                context, match.prompt(), match.button(), StrategyCardRanking.initiative(match.button())));
    }
}
