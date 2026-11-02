package ti4.service.draft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;

@UtilityClass
class DraftEventRecorder {

    private static final String STARTED_KEY = "gameEventDraftStarted";

    static void reset(Game game) {
        game.removeStoredValue(STARTED_KEY);
    }

    static void recordStarted(DraftManager draftManager) {
        Game game = draftManager.getGame();
        if (!game.getStoredValue(STARTED_KEY).isEmpty()) {
            return;
        }
        game.setStoredValue(STARTED_KEY, "yes");

        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", draftManager.getOrchestrator().getClass().getSimpleName());
        payload.put(
                "players",
                draftManager.getPlayerStates().keySet().stream()
                        .map(userId -> userNameOf(game, userId))
                        .sorted()
                        .toList());
        payload.put("choices", choicesByDraftable(draftManager));
        GameEventService.commit(game, GameEventType.DRAFT_STARTED, null, payload);
    }

    static void recordPick(
            DraftManager draftManager,
            Player player,
            Draftable draftable,
            DraftChoice choice,
            DraftManager.CommandSource source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userName", player.getUserName());
        payload.put("draftable", draftable.getType().toString());
        payload.put("choice", choice.choiceKey());
        payload.put("source", source.name());
        GameEventService.commit(draftManager.getGame(), GameEventType.DRAFT_PICK, player, payload);
    }

    static void recordEnded(DraftManager draftManager) {
        Game game = draftManager.getGame();
        Map<String, Map<String, List<String>>> picksByPlayer = new LinkedHashMap<>();
        draftManager.getPlayerStates().forEach((userId, state) -> {
            Map<String, List<String>> picks = new LinkedHashMap<>();
            for (Draftable draftable : draftManager.getDraftables()) {
                picks.put(
                        draftable.getType().toString(),
                        state.getPicks(draftable.getType()).stream()
                                .map(DraftChoice::choiceKey)
                                .toList());
            }
            picksByPlayer.put(userNameOf(game, userId), picks);
        });
        GameEventService.commit(game, GameEventType.DRAFT_ENDED, null, Map.of("picks", picksByPlayer));
    }

    private static Map<String, List<String>> choicesByDraftable(DraftManager draftManager) {
        Map<String, List<String>> choices = new LinkedHashMap<>();
        for (Draftable draftable : draftManager.getDraftables()) {
            choices.put(
                    draftable.getType().toString(),
                    draftable.getAllDraftChoices().stream()
                            .map(DraftChoice::choiceKey)
                            .toList());
        }
        return choices;
    }

    private static String userNameOf(Game game, String userId) {
        Player player = game.getPlayer(userId);
        return player == null ? userId : player.getUserName();
    }
}
