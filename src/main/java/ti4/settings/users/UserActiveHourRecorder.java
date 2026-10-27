package ti4.settings.users;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserActiveHourRecorder {

    private static final int HOURS_PER_DAY = 24;
    private static final ConcurrentMap<String, int[]> pendingCheckinsByUserId = new ConcurrentHashMap<>();

    public static void record(String userId) {
        int currentUtcHour = ZonedDateTime.now(ZoneOffset.UTC).getHour();
        pendingCheckinsByUserId.compute(userId, (_, checkins) -> {
            int[] updatedCheckins = checkins == null ? new int[HOURS_PER_DAY] : checkins;
            updatedCheckins[currentUtcHour]++;
            return updatedCheckins;
        });
    }

    public static void flush() {
        for (String userId : List.copyOf(pendingCheckinsByUserId.keySet())) {
            int[] checkins = pendingCheckinsByUserId.remove(userId);
            if (checkins != null) {
                UserSettingsManager.addActiveHourCheckins(userId, checkins);
            }
        }
    }
}
