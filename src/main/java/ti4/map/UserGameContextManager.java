package ti4.map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jetbrains.annotations.Nullable;

public class UserGameContextManager {

    private static final ConcurrentMap<String, String> userIdToCurrentGameName = new ConcurrentHashMap<>();

    public static boolean setContextGame(String userId, String gameName) {
        if (GameManager.isValidGame(gameName)) {
            userIdToCurrentGameName.put(userId, gameName);
            return true;
        }
        return false;
    }

    public static void resetContextGame(String userId) {
        userIdToCurrentGameName.remove(userId);
    }

    public static boolean doesUserHaveContextGame(String userId) {
        return userIdToCurrentGameName.containsKey(userId);
    }

    @Nullable
    public static String getContextGame(String userId) {
        return userIdToCurrentGameName.get(userId);
    }
}
