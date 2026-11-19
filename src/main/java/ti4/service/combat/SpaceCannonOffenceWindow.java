package ti4.service.combat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SpaceCannonOffenceWindow {

    private static final String KEY = "spaceCannonOffenceResolved";
    private static final String FIELD = "|";
    private static final String FACTIONS = ",";

    public static void open(Game game) {
        game.removeStoredValue(KEY);
    }

    public static void resolve(Game game, Player player, String position) {
        if (player == null || StringUtils.isBlank(position)) return;
        Set<String> resolved = resolvedBy(game, position);
        resolved.add(player.getFaction());
        game.setStoredValue(KEY, scope(game, position) + FIELD + String.join(FACTIONS, resolved));
    }

    public static Set<String> resolvedBy(Game game, String position) {
        String stored = game.getStoredValue(KEY);
        String scope = scope(game, position) + FIELD;
        if (!stored.startsWith(scope)) return new LinkedHashSet<>();
        return Arrays.stream(stored.substring(scope.length()).split(FACTIONS))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String scope(Game game, String position) {
        Player active = game.getActivePlayer();
        String activeFaction = active == null ? "" : active.getFaction();
        return game.getRound() + FIELD + activeFaction + FIELD + position;
    }
}
