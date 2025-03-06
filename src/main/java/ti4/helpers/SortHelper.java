package ti4.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@UtilityClass
public class SortHelper {

    public static Map<String, Integer> sortByValue(Map<String, Integer> unsortMap, boolean order) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(unsortMap.entrySet());

        // Sorting the list based on values
        list.sort((o1, o2) -> order ? o1.getValue().compareTo(o2.getValue()) == 0
            ? o1.getKey().compareTo(o2.getKey())
            : o1.getValue().compareTo(o2.getValue())
            : o2.getValue().compareTo(o1.getValue()) == 0
                ? o2.getKey().compareTo(o1.getKey())
                : o2.getValue().compareTo(o1.getValue()));
        return list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    public static void sortButtonsByTitle(List<Button> buttons) {
        buttons.sort((b1, b2) -> b1.getLabel().compareToIgnoreCase(b2.getLabel()));
    }
}
