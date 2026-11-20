package ti4.service.milty;

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
class MiltyDraftEventRecorder {

    static final String ENDED_KEY = "gameEventMiltyDraftEnded";

    static void recordStarted(Game game, MiltyDraftManager manager) {
        game.removeStoredValue(ENDED_KEY);
        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", "Milty");
        payload.put("mapTemplate", manager.getMapTemplate());
        payload.put(
                "pickOrder",
                manager.getDraftOrder().stream()
                        .map(userId -> userNameOf(game, userId))
                        .toList());
        payload.put("slices", slicesByName(manager));
        payload.put("factions", manager.getFactionDraft());
        GameEventService.commit(game, GameEventType.DRAFT_STARTED, null, payload);
    }

    static void recordPick(Game game, Player player, String category, String item, String source) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userName", player.getUserName());
        payload.put("draftable", category);
        payload.put("choice", item);
        payload.put("source", source);
        GameEventService.commit(game, GameEventType.DRAFT_PICK, player, payload);
    }

    static void recordEnded(Game game, MiltyDraftManager manager) {
        if (!game.getStoredValue(ENDED_KEY).isEmpty()) {
            return;
        }
        game.setStoredValue(ENDED_KEY, "yes");

        Map<String, Map<String, List<String>>> picksByPlayer = new LinkedHashMap<>();
        for (String userId : manager.getPlayers()) {
            MiltyDraftManager.PlayerDraft draft = manager.getPlayerDraft(userId);
            Map<String, List<String>> picks = new LinkedHashMap<>();
            picks.put(
                    "slice",
                    draft == null || draft.getSlice() == null
                            ? List.of()
                            : List.of(draft.getSlice().getName()));
            picks.put("faction", draft == null || draft.getFaction() == null ? List.of() : List.of(draft.getFaction()));
            picks.put(
                    "order",
                    draft == null || draft.getPosition() == null
                            ? List.of()
                            : List.of(String.valueOf(draft.getPosition())));
            picksByPlayer.put(userNameOf(game, userId), picks);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", "Milty");
        payload.put("picks", picksByPlayer);
        GameEventService.commit(game, GameEventType.DRAFT_ENDED, null, payload);
    }

    private static Map<String, List<String>> slicesByName(MiltyDraftManager manager) {
        Map<String, List<String>> slices = new LinkedHashMap<>();
        for (MiltyDraftSlice slice : manager.getSlices()) {
            slices.put(
                    slice.getName(),
                    slice.getTiles().stream()
                            .map(tile -> tile.getTile().getTileID())
                            .toList());
        }
        return slices;
    }

    private static String userNameOf(Game game, String userId) {
        Player player = game.getPlayer(userId);
        return player == null ? userId : player.getUserName();
    }
}
