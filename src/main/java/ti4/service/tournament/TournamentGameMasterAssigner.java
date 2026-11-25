package ti4.service.tournament;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TournamentGameMasterAssigner {

    public static Map<Integer, String> assign(
            List<String> gameMasterIds, Map<Integer, ? extends Collection<String>> playersByGroup) {
        Map<Integer, String> assignments = new TreeMap<>();
        if (gameMasterIds.isEmpty()) return assignments;
        int next = 0;
        for (Map.Entry<Integer, ? extends Collection<String>> group : new TreeMap<>(playersByGroup).entrySet()) {
            for (int tried = 0; tried < gameMasterIds.size(); tried++) {
                String candidate = gameMasterIds.get((next + tried) % gameMasterIds.size());
                if (group.getValue().contains(candidate)) continue;
                assignments.put(group.getKey(), candidate);
                next = (next + tried + 1) % gameMasterIds.size();
                break;
            }
        }
        return assignments;
    }
}
