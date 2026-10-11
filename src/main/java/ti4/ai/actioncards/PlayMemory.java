package ti4.ai.actioncards;

import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
class PlayMemory {

    static final String PLAY_KEY = "acPlay|";
    static final String TURN_SCOPE = "turn:";
    static final String SUMMIT_SCOPE = "summit";

    static String turnKey(AiTurnContext context) {
        return PLAY_KEY + TURN_SCOPE + context.turnKey();
    }

    static String summitKey() {
        return PLAY_KEY + SUMMIT_SCOPE;
    }

    static void store(AiTurnContext context, String key, CardPlay play) {
        context.memory().refresh(key, play.encode());
    }

    static void moveTo(AiTurnContext context, String key, CardPlay play, Stage stage) {
        store(context, key, play.at(stage, context.now()));
    }
}
