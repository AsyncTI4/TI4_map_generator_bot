package ti4.spring.service.gameevent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class GameStartedRecorder {

    private static final String RECORDED_KEY = "gameEventGameStarted";

    public static void recordOnce(Game game) {
        if (!game.getStoredValue(RECORDED_KEY).isEmpty()) {
            return;
        }
        game.setStoredValue(RECORDED_KEY, "yes");

        Map<String, Object> payload = new HashMap<>();
        payload.put("players", playersOf(game));
        payload.put("vpGoal", game.getVp());
        payload.put("mapTemplate", game.getMapTemplateID());
        payload.put("actionCardDeck", game.getAcDeckID());
        payload.put("secretObjectiveDeck", game.getSoDeckID());
        payload.put("agendaDeck", game.getAgendaDeckID());
        payload.put("fogOfWar", game.isFowMode());
        Player speaker = game.getSpeaker();
        if (speaker != null) {
            payload.put("speaker", speaker.getFaction());
        }
        GameEventService.commit(game, GameEventType.GAME_STARTED, null, payload);
    }

    private static List<Map<String, Object>> playersOf(Game game) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("faction", player.getFaction());
            entry.put("color", player.getColor());
            entry.put("userName", player.getUserName());
            Tile homeSystem = player.getHomeSystemTile();
            if (homeSystem != null) {
                entry.put("homeSystem", homeSystem.getPosition());
            }
            players.add(entry);
        }
        return players;
    }
}
