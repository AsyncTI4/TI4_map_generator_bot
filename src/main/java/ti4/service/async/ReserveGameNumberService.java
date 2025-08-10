package ti4.service.async;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import ti4.json.PersistenceManager;
import ti4.map.persistence.GameManager;
import ti4.message.BotLogger;

public class ReserveGameNumberService {

    private static final String fileName = "reservedGameNumbers.json";
    private static List<String> reservedGameCache = null;

    public static void addReservedGame(String gameNum) {
        List<String> reserved = readReservedList();
        if (!reserved.contains(gameNum)) {
            reserved.add(gameNum);
            saveReservedList(reserved);
        }
    }

    public static void removeReservedGame(String gameNum) {
        List<String> reserved = readReservedList();
        if (reserved.contains(gameNum)) {
            reserved.remove(gameNum);
            saveReservedList(reserved);
        }
    }

    public static boolean isGameNumReserved(String gameNum) {
        return readReservedList().contains(gameNum);
    }

    public static String summarizeReservedGames() {
        StringBuilder sb = new StringBuilder("__**Currently Reserved Game Numbers:**__");
        reservedGameCache.stream().sorted().forEach(g -> sb.append("\n> ").append(g));
        return sb.toString();
    }

    private static List<String> filterOutRealGames(List<String> reserved) {
        if (reserved == null) return new ArrayList<>();
        List<String> reservedAndNotTaken = reserved.stream()
                .filter(Objects::nonNull)
                .filter(Predicate.not(GameManager::isValid))
                .toList();
        return new ArrayList<>(reservedAndNotTaken);
    }

    private static List<String> readReservedList() {
        if (reservedGameCache == null) {
            try {
                List<String> reserved = PersistenceManager.readListFromJsonFile(fileName, String.class);
                reservedGameCache = filterOutRealGames(reserved);
            } catch (Exception e) {
                BotLogger.error("Failed to read json data for Reserved Game Cache.", e);
                reservedGameCache = new ArrayList<>();
            }
        }
        return reservedGameCache;
    }

    private static void saveReservedList(List<String> reserved) {
        try {
            List<String> reservedAndNotTaken = filterOutRealGames(reserved);
            PersistenceManager.writeObjectToJsonFile(fileName, reservedAndNotTaken);
            reservedGameCache = reservedAndNotTaken;
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for Reserved Game Cache.", e);
        }
    }
}
