package ti4.settings.users;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserActiveHourRecorder {

    private static final int HOURS_PER_DAY = 24;
    private static final ConcurrentMap<String, int[]> pendingCheckinsByUserId = new ConcurrentHashMap<>();

    public static void record(String userId) {
        int currentUtcHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        addPending(userId, hourCheckins -> hourCheckins[currentUtcHour]++);
    }

    public static int flush() {
        Map<String, int[]> checkinsByUserId = takePending();
        if (checkinsByUserId.isEmpty()) return 0;
        if (!UserSettingsManager.addActiveHourCheckins(checkinsByUserId)) {
            checkinsByUserId.forEach(UserActiveHourRecorder::restorePending);
            return 0;
        }
        return checkinsByUserId.size();
    }

    private static Map<String, int[]> takePending() {
        Map<String, int[]> taken = new HashMap<>();
        for (String userId : List.copyOf(pendingCheckinsByUserId.keySet())) {
            int[] checkins = pendingCheckinsByUserId.remove(userId);
            if (checkins != null) {
                taken.put(userId, checkins);
            }
        }
        return taken;
    }

    private static void restorePending(String userId, int[] checkins) {
        addPending(userId, hourCheckins -> {
            for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
                hourCheckins[hour] += checkins[hour];
            }
        });
    }

    private static void addPending(String userId, Consumer<int[]> change) {
        pendingCheckinsByUserId.compute(userId, (_, checkins) -> {
            int[] updatedCheckins = checkins == null ? new int[HOURS_PER_DAY] : checkins;
            change.accept(updatedCheckins);
            return updatedCheckins;
        });
    }
}
