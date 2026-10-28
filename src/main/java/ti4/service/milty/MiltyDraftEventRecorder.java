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

    static void recordStarted(Game game, MiltyDraftManager manager) {
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
