package ti4.draft;

import java.util.ArrayList;
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
public class BagDraftEventRecorder {

    static final String ENDED_KEY = "gameEventBagDraftEnded";

    public static void recordStarted(Game game) {
        game.removeStoredValue(ENDED_KEY);
        BagDraft draft = game.getActiveBagDraft();
        Map<String, List<String>> bags = new LinkedHashMap<>();
        Map<String, List<String>> choices = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) {
            List<DraftItem> contents = bagContents(player);
            bags.put(player.getUserName(), aliasesOf(contents));
            for (DraftItem item : contents) {
                choices.computeIfAbsent(categoryOf(item), _ -> new ArrayList<>())
                        .add(item.getAlias());
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", orchestratorOf(draft));
        payload.put("draftType", draft == null ? null : draft.getSaveString());
        payload.put(
                "players",
                game.getRealPlayers().stream().map(Player::getUserName).toList());
        payload.put("bags", bags);
        payload.put("choices", choices);
        GameEventService.commit(game, GameEventType.DRAFT_STARTED, null, payload);
    }

    public static void recordAuctionStarted(Game game, List<Player> nominationOrder, Map<String, Integer> settings) {
        game.removeStoredValue(ENDED_KEY);
        BagDraft draft = game.getActiveBagDraft();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", orchestratorOf(draft));
        payload.put("draftType", draft == null ? null : draft.getSaveString());
        payload.put("players", nominationOrder.stream().map(Player::getUserName).toList());
        payload.put("settings", settings);
        GameEventService.commit(game, GameEventType.DRAFT_STARTED, null, payload);
    }

    public static void recordPicks(Game game, Player player, List<DraftItem> items, String source) {
        for (DraftItem item : List.copyOf(items)) {
            recordPick(game, player, item, source, Map.of());
        }
    }

    public static void recordPick(
            Game game, Player player, DraftItem item, String source, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>(details);
        payload.put("userName", player.getUserName());
        payload.put("draftable", categoryOf(item));
        payload.put("choice", item.getAlias());
        payload.put("source", source);
        GameEventService.commit(game, GameEventType.DRAFT_PICK, player, payload);
    }

    public static void recordEnded(Game game) {
        if (!game.getStoredValue(ENDED_KEY).isEmpty()) {
            return;
        }
        game.setStoredValue(ENDED_KEY, "yes");

        Map<String, Map<String, List<String>>> picksByPlayer = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) {
            Map<String, List<String>> picks = new LinkedHashMap<>();
            DraftBag hand = player.getDraftHand();
            List<DraftItem> contents = hand == null ? List.of() : hand.Contents;
            for (DraftItem item : contents) {
                picks.computeIfAbsent(categoryOf(item), _ -> new ArrayList<>()).add(item.getAlias());
            }
            picksByPlayer.put(player.getUserName(), picks);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orchestrator", orchestratorOf(game.getActiveBagDraft()));
        payload.put("picks", picksByPlayer);
        GameEventService.commit(game, GameEventType.DRAFT_ENDED, null, payload);
    }

    private static List<DraftItem> bagContents(Player player) {
        DraftBag bag = player.getCurrentDraftBag();
        return bag == null ? List.of() : bag.Contents;
    }

    private static List<String> aliasesOf(List<DraftItem> items) {
        return items.stream().map(DraftItem::getAlias).toList();
    }

    private static String categoryOf(DraftItem item) {
        return String.valueOf(item.getItemCategory());
    }

    private static String orchestratorOf(BagDraft draft) {
        return draft == null ? "BagDraft" : draft.getClass().getSimpleName();
    }
}
