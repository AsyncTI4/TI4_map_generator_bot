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
    static final String MASTER_PLAN_SCOPE = "plan:";
    static final String AGENDA_SCOPE = "agenda:";
    static final String PRODUCTION_SCOPE = "build:";
    static final String COMBAT_PREFIX = PLAY_KEY + "combat:";
    private static final String START_SUFFIX = ":start";
    private static final String ANNOUNCE_KEY = "acAnnounce|";

    static String turnKey(AiTurnContext context) {
        return PLAY_KEY + TURN_SCOPE + context.turnKey();
    }

    static String masterPlanKey(AiTurnContext context) {
        return PLAY_KEY + MASTER_PLAN_SCOPE + context.turnKey();
    }

    static String summitKey() {
        return PLAY_KEY + SUMMIT_SCOPE;
    }

    static String agendaKey(AiTurnContext context) {
        return PLAY_KEY + AGENDA_SCOPE + context.game().getCurrentAgendaInfo();
    }

    static String productionKey(String scope) {
        return PLAY_KEY + PRODUCTION_SCOPE + scope;
    }

    static String reactionKey(AiTurnContext context) {
        return PLAY_KEY + MESSAGE_SCOPE + context.turnKey() + "@"
                + context.game().getActiveSystem();
    }

    static String combatKey(String position, String holder, int round) {
        return COMBAT_PREFIX + position + "@" + holder + "#" + round + START_SUFFIX;
    }

    static String combatPosition(String key) {
        String scope = key.substring(COMBAT_PREFIX.length());
        return scope.substring(0, Math.max(0, scope.indexOf('@')));
    }

    static List<String> activeKeys(AiTurnContext context) {
        List<String> keys =
                new ArrayList<>(List.of(turnKey(context), masterPlanKey(context), summitKey(), agendaKey(context)));
        keys.addAll(context.memory().keysStartingWith(PLAY_KEY + MESSAGE_SCOPE));
        keys.addAll(context.memory().keysStartingWith(PLAY_KEY + PRODUCTION_SCOPE));
        keys.addAll(context.memory().keysStartingWith(COMBAT_PREFIX));
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
