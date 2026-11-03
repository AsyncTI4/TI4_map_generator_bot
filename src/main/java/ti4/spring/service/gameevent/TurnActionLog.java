package ti4.spring.service.gameevent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

@UtilityClass
public class TurnActionLog {

    public static final String TACTICAL = "TACTICAL";
    public static final String STRATEGIC = "STRATEGIC";
    public static final String COMPONENT = "COMPONENT";

    private static final String KEY = "gameEventTurnActions";
    private static final String SEPARATOR = ",";

    public static void record(Game game, String actionType) {
        String existing = game.getStoredValue(KEY);
        game.setStoredValue(KEY, StringUtils.isEmpty(existing) ? actionType : existing + SEPARATOR + actionType);
    }

    public static void recordComponent(Game game, String componentKind) {
        record(game, COMPONENT + ":" + componentKind);
    }

    public static Map<String, Object> turnPayload(Game game, boolean passed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("passed", passed);
        payload.put("actions", drain(game));
        return payload;
    }

    public static List<String> drain(Game game) {
        String existing = game.getStoredValue(KEY);
        game.removeStoredValue(KEY);
        if (StringUtils.isEmpty(existing)) {
            return List.of();
        }
        return Arrays.asList(existing.split(SEPARATOR));
    }
}
