package ti4.service.storage;

import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ByteSizes {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    public static String format(long bytes) {
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? bytes + " " + UNITS[0] : String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }
}
