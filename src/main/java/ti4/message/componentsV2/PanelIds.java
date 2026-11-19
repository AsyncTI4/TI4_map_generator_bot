package ti4.message.componentsV2;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class PanelIds {

    private static final String SEPARATOR = "_";

    public static String of(String prefix, Object key, Object... args) {
        StringBuilder id = new StringBuilder(prefix).append(key);
        for (Object arg : args) {
            id.append(SEPARATOR).append(arg);
        }
        return id.toString();
    }

    public static long key(String componentId, String prefix) {
        return Long.parseLong(StringUtils.substringBefore(componentId.substring(prefix.length()), SEPARATOR));
    }

    public static String arg(String componentId, String prefix) {
        return StringUtils.substringAfter(componentId.substring(prefix.length()), SEPARATOR);
    }
}
