package ti4.helpers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.ISnowflake;

@UtilityClass
public class DateTimeHelper {

    public static long getLongDateTimeFromDiscordSnowflake(long snowflake) {
        return (snowflake >> 22) + 1420070400000L;
    }

    public static long getLongDateTimeFromDiscordSnowflake(ISnowflake snowflake) {
        return getLongDateTimeFromDiscordSnowflake(snowflake.getIdLong());
    }

    public static String getTimeRepresentationToSeconds(long totalMillis) {
        long totalSeconds = totalMillis / 1000; // total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; // total minutes (truncates)
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60; // total hours (truncates)

        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }

    public static String getTimeRepresentationToMilliseconds(long totalMillis) {
        long millis = (totalMillis % 1000);
        long totalSeconds = totalMillis / 1000; // total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; // total minutes (truncates)
        long minutes = totalMinutes % 60;

        return String.format("%02dm:%02ds:%04dms", minutes, seconds, millis);
    }

    public static String getTimeRepresentationNanoSeconds(long totalNanoSeconds) {
        long totalMicroSeconds = totalNanoSeconds / 1000;
        long totalMilliSeconds = totalMicroSeconds / 1000;
        long totalSeconds = totalMilliSeconds / 1000;
        // long totalMinutes = totalSeconds / 60;
        // long totalHours = totalMinutes / 60;
        // long totalDays = totalHours / 24;

        long nanoSeconds = totalNanoSeconds % 1000;
        long microSeconds = totalMicroSeconds % 1000;
        long milleSeconds = totalMilliSeconds % 1000;
        // long minutes = totalMinutes % 60;
        // long hours = totalHours % 24;
        // long days = totalDays;

        // sb.append(String.format("%d:", days));
        // sb.append(String.format("%02dh:", hours));
        // sb.append(String.format("%02dm:", minutes));

        return String.format("%02ds:", totalSeconds) +
            String.format("%03d:", milleSeconds) +
            String.format("%03d:", microSeconds) +
            String.format("%03d", nanoSeconds);
    }

}
