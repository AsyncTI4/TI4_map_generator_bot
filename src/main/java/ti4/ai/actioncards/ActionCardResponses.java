package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

@UtilityClass
public class ActionCardResponses {

    private static final String SPY_SEND = "spyStep3_";
    private static final String FORCED_NOTE_TEXT = "forced to give a promissory note";
    private static final String NOTE_CHOICE_TEXT = "promissory note you wish to send";
    private static final String NOTE_SEND = "naaluHeroSend_";
    private static final String CONCEDE_DURESS = "concedeToED_";
    private static final String DURESS_TITLE = "Extreme Duress";
    private static final String SPY_TITLE = "Spy";
    private static final String PRESSURE_TITLE = "Diplomatic Pressure";
    private static final String DURESS_KEY = "extremeDuress|";
    private static final String STRATEGIC = "strategic";
    private static final String STRATEGIC_ACTION = "strategicAction_";

    public static Optional<AiDecision> respond(AiTurnContext context) {
        return sendSpyCard(context).or(() -> giveForcedNote(context)).or(() -> extremeDuress(context));
    }

    private static Optional<AiDecision> sendSpyCard(AiTurnContext context) {
        if (context.seat().getAcCount() == 0 || pending(context.game(), SPY_TITLE)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> send = prompt.firstEnabled(
                    button -> button.isUnowned() && button.handlerId().startsWith(SPY_SEND));
            if (send.isPresent() && !context.alreadyPressed(prompt, send.get())) {
                return Optional.of(AiDecision.press(prompt, send.get(), "send a random action card to a Spy"));
            }
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> giveForcedNote(AiTurnContext context) {
        Game game = context.game();
        Map<String, Integer> notes = context.seat().getPromissoryNotes();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden() || !asksForANote(prompt)) continue;
            if (prompt.content().contains(FORCED_NOTE_TEXT) && pending(game, PRESSURE_TITLE)) continue;
            List<PromptButton> gives = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() && button.handlerId().startsWith(NOTE_SEND))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .filter(button -> noteFor(notes, button).isPresent())
                    .toList();
            if (gives.isEmpty()) continue;
            Optional<PromptButton> owed = NoteGiving.owed(context, receiverOf(gives.getFirst()))
                    .flatMap(alias -> gives.stream()
                            .filter(button ->
                                    noteFor(notes, button).filter(alias::equals).isPresent())
                            .findFirst());
            if (owed.isPresent()) {
                return Optional.of(
                        AiDecision.press(prompt, owed.get(), "give the promissory note it priced in a deal"));
            }
            PromptButton give = gives.stream()
                    .min(Comparator.comparingInt(button ->
                            NoteGiving.giveRank(game, noteFor(notes, button).orElse(""), receiverOf(button))))
                    .orElseThrow();
            return Optional.of(AiDecision.press(prompt, give, "give the least harmful promissory note"));
        }
        return Optional.empty();
    }

    private static boolean asksForANote(AiPrompt prompt) {
        String content = prompt.content();
        return content.contains(FORCED_NOTE_TEXT) || StringUtils.containsIgnoreCase(content, NOTE_CHOICE_TEXT);
    }

    private static Optional<String> noteFor(Map<String, Integer> notes, PromptButton button) {
        String index = StringUtils.substringAfterLast(button.handlerId(), "_");
        if (!StringUtils.isNumeric(index)) return Optional.empty();
        int wanted = Integer.parseInt(index);
        return notes.entrySet().stream()
                .filter(entry -> entry.getValue() == wanted)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static String receiverOf(PromptButton button) {
        return StringUtils.substringBetween(button.handlerId(), NOTE_SEND, "_");
    }

    private static Optional<AiDecision> extremeDuress(AiTurnContext context) {
        String key = DURESS_KEY + context.turnKey();
        if (STRATEGIC.equals(context.memory().get(key).orElse(""))) return playStrategyCard(context, key);
        List<AiPrompt> turn = Prompts.thisTurn(context);
        Optional<Match> concede = Prompts.owned(turn, context.faction(), id -> id.startsWith(CONCEDE_DURESS))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (concede.isEmpty()) return Optional.empty();
        Optional<PromptButton> giveIn = concede.get()
                .prompt()
                .firstEnabled(button -> button.isUnowned() && "deleteButtons".equals(button.handlerId()));
        if (latestDuressCanceled(context.game()) && giveIn.isPresent()) {
            return Optional.of(AiDecision.press(concede.get().prompt(), giveIn.get(), "Extreme Duress was sabotaged"));
        }
        if (TacticalRules.actionTaken(context)
                || giveIn.isEmpty()
                || strategyCardToPlay(context).isEmpty()) {
            if (pending(context.game(), DURESS_TITLE)) return Optional.empty();
            return Optional.of(concede.get().press("concede to Extreme Duress"));
        }
        context.memory().put(key, STRATEGIC);
        return Optional.of(AiDecision.press(
                concede.get().prompt(), giveIn.get(), "give in to Extreme Duress and play a strategy card"));
    }

    private static Optional<AiDecision> playStrategyCard(AiTurnContext context, String key) {
        if (TacticalRules.actionTaken(context)) {
            context.memory().put(key, "done");
            return Optional.empty();
        }
        return strategyCardToPlay(context)
                .map(match -> StrategyCardRules.play(
                        context, match.prompt(), match.button(), StrategyCardRanking.initiative(match.button())));
    }

    private static Optional<Match> strategyCardToPlay(AiTurnContext context) {
        Game game = context.game();
        return Prompts.owned(Prompts.thisTurn(context), context.faction(), id -> id.startsWith(STRATEGIC_ACTION))
                .filter(match -> {
                    int initiative = StrategyCardRanking.initiative(match.button());
                    return initiative > 0 && !game.getPlayedSCs().contains(initiative);
                });
    }

    private static boolean pending(Game game, String title) {
        if (latestCanceled(game, title)) return true;
        List<String> others =
                game.getRealPlayers().stream().map(Player::getFaction).toList();
        for (GameMessage window : GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD)) {
            long missing = others.stream()
                    .filter(faction -> !window.factionsThatReacted().contains(faction))
                    .count();
            if (missing > 1) return true;
        }
        return false;
    }

    private static boolean latestCanceled(Game game, String title) {
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = plays.size() - 1; index >= 0; index--) {
            if (title.equals(plays.get(index).getActionCard()))
                return plays.get(index).isCanceled();
        }
        return false;
    }

    private static boolean latestDuressCanceled(Game game) {
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = plays.size() - 1; index >= 0; index--) {
            if (DURESS_TITLE.equals(plays.get(index).getActionCard()))
                return plays.get(index).isCanceled();
        }
        return false;
    }
}
