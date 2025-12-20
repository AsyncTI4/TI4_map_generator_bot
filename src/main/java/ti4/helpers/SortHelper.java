package ti4.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;

@UtilityClass
public class SortHelper {

    public static Map<String, Integer> sortByValue(Map<String, Integer> unsortMap, boolean order) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(unsortMap.entrySet());

        Comparator<Map.Entry<String, Integer>> comparator =
                Comparator.comparing(Map.Entry<String, Integer>::getValue).thenComparing(Map.Entry::getKey);

        list.sort(order ? comparator : comparator.reversed());

        return list.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    public static void sortButtonsByTitle(List<Button> buttons) {
        buttons.sort(Comparator.comparing(Button::getLabel, String.CASE_INSENSITIVE_ORDER));
    }
}
