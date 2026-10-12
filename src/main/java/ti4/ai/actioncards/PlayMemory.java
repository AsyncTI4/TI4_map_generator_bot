package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
class PlayMemory {

    static final String PLAY_KEY = "acPlay|";
    static final String TURN_SCOPE = "turn:";
    static final String MESSAGE_SCOPE = "msg:";
    static final String SUMMIT_SCOPE = "summit";
    private static final String ANNOUNCE_KEY = "acAnnounce|";

    static String turnKey(AiTurnContext context) {
        return PLAY_KEY + TURN_SCOPE + context.turnKey();
    }

    static String summitKey() {
        return PLAY_KEY + SUMMIT_SCOPE;
    }

    static String reactionKey(AiTurnContext context) {
        return PLAY_KEY + MESSAGE_SCOPE + context.turnKey() + "@"
                + context.game().getActiveSystem();
    }

    static List<String> activeKeys(AiTurnContext context) {
        List<String> keys = new ArrayList<>(List.of(turnKey(context), summitKey()));
        keys.addAll(context.memory().keysStartingWith(PLAY_KEY + MESSAGE_SCOPE));
        return keys;
    }

    static void store(AiTurnContext context, String key, CardPlay play) {
        context.memory().refresh(key, play.encode());
    }

    static void moveTo(AiTurnContext context, String key, CardPlay play, Stage stage) {
        store(context, key, play.at(stage, context.now()));
    }

    static void rememberAnnouncement(AiTurnContext context, String key, String text) {
        if (text.isBlank()) return;
        context.memory().put(ANNOUNCE_KEY + key, text);
    }

    static Optional<String> takeAnnouncement(AiTurnContext context, String key) {
        Optional<String> text = context.memory().get(ANNOUNCE_KEY + key);
        context.memory().remove(ANNOUNCE_KEY + key);
        return text;
    }
}
