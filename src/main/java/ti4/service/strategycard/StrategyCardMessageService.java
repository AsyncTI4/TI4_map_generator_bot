package ti4.service.strategycard;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

@UtilityClass
public class StrategyCardMessageService {

    public static Optional<GameMessage> getStrategyCardMessage(String gameName, int round, int sc) {
        String key = getKey(round, sc);
        return GameMessageManager.getAll(gameName, GameMessageType.STRATEGY_CARD).stream()
                .filter(message -> Objects.equals(message.key(), key))
                .findFirst();
    }

    public static void replaceStrategyCardMessage(
            String gameName, String messageId, int round, int sc, long gameSaveTime) {
        GameMessageManager.replace(gameName, messageId, GameMessageType.STRATEGY_CARD, gameSaveTime, getKey(round, sc));
    }

    public static String getKey(int round, int sc) {
        return round + "::" + sc;
    }
}
