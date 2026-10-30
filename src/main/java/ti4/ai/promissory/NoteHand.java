package ti4.ai.promissory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
class NoteHand {

    private static final String PLAY_PREFIX = "resolvePNPlay_";
    private static final String NOTES_INFO = "refreshPNInfo";
    private static final String CARDS_INFO = "cardsInfo";
    private static final String BUTTON_KEY = "noteHandButton|";
    private static final String REFRESH_KEY = "noteHandRefresh|";
    private static final String FIELD = "~";
    private static final int BUTTON_FIELDS = 4;
    private static final long REFRESH_WAIT_MILLIS = 20_000L;
    private static final long REFRESH_POLL_MILLIS = 3_000L;

    static boolean holdsForeign(Player seat, String noteId) {
        return seat.getPromissoryNotes().containsKey(noteId)
                && !seat.ownsPromissoryNote(noteId)
                && !seat.getPromissoryNotesInPlayArea().contains(noteId);
    }

    static void rememberPlayButtons(AiTurnContext context) {
        Set<String> seen = new HashSet<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (!isPlayButton(button)) continue;
                String noteId = StringUtils.removeStart(button.handlerId(), PLAY_PREFIX);
                if (!seen.add(noteId)) continue;
                context.memory()
                        .put(
                                BUTTON_KEY + noteId,
                                String.join(
                                        FIELD,
                                        prompt.channelId(),
                                        prompt.messageId(),
                                        button.customId(),
                                        button.label()));
            }
        }
    }

    static Optional<AiDecision> play(AiTurnContext context, String noteId, String scope, String reason) {
        return button(context, noteId).map(match -> match.press(reason)).or(() -> requestHand(context, scope));
    }

    static Optional<Match> button(AiTurnContext context, String noteId) {
        String handlerId = PLAY_PREFIX + noteId;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> play = prompt.firstEnabled(candidate -> candidate.isUnowned()
                    && handlerId.equals(candidate.handlerId())
                    && !context.alreadyPressed(prompt, candidate));
            if (play.isPresent()) return Optional.of(new Match(prompt, play.get()));
        }
        return remembered(context, noteId);
    }

    static Optional<AiDecision> requestHand(AiTurnContext context, String scope) {
        String key = REFRESH_KEY + context.turnKey() + "|" + scope;
        Optional<Long> asked =
                context.memory().get(key).filter(StringUtils::isNumeric).map(Long::parseLong);
        if (asked.isPresent()) {
            long until = asked.get() + REFRESH_WAIT_MILLIS;
            if (context.now() >= until) return Optional.empty();
            return Optional.of(
                    new AiDecision.Wait(Math.min(until, context.now() + REFRESH_POLL_MILLIS), "its promissory notes"));
        }
        Optional<Match> refresh = hiddenButton(context, NOTES_INFO).or(() -> hiddenButton(context, CARDS_INFO));
        refresh.ifPresent(ignored -> context.memory().put(key, String.valueOf(context.now())));
        return refresh.map(match -> match.press("bring its promissory notes back into view"));
    }

    private static boolean isPlayButton(PromptButton button) {
        return button.isUnowned() && button.handlerId().startsWith(PLAY_PREFIX);
    }

    private static Optional<Match> remembered(AiTurnContext context, String noteId) {
        String key = BUTTON_KEY + noteId;
        String[] fields =
                StringUtils.splitPreserveAllTokens(context.memory().get(key).orElse(""), FIELD);
        if (fields == null || fields.length != BUTTON_FIELDS) return Optional.empty();
        context.memory().remove(key);
        PromptButton button = new PromptButton(0, fields[2], PLAY_PREFIX + noteId, null, fields[3], false);
        AiPrompt prompt =
                new AiPrompt(fields[0], fields[1], PromptSource.AI_THREAD, "", List.of(button), context.now());
        if (context.alreadyPressed(prompt, button)) return Optional.empty();
        return Optional.of(new Match(prompt, button));
    }

    private static Optional<Match> hiddenButton(AiTurnContext context, String handlerId) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.firstEnabled(
                    candidate -> handlerId.equals(candidate.handlerId()) && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }
}
