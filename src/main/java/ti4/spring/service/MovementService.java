package ti4.spring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.helpers.AliasHandler;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.service.tactical.TacticalActionDisplacementService;
import ti4.service.tactical.TacticalActionOutputService;
import ti4.spring.model.MovementUnitCount;

@RequiredArgsConstructor
@Service
public class MovementService {

    /**
     * Apply the provided displacement to the given target position, handle token placement, and return the updated tile.
     */
    public Tile commitMovement(
            Game game, Player player, String targetPosition, Map<String, List<MovementUnitCount>> displacement) {
        // Transform web payload into internal displacement structure, normalizing unit-holder keys
        Map<String, Map<UnitKey, List<Integer>>> internal = new HashMap<>();
        if (displacement != null) {
            for (Entry<String, List<MovementUnitCount>> entry : displacement.entrySet()) {
                String normalizedKey = normalizeUnitHolderKey(game, entry.getKey());
                List<MovementUnitCount> units = entry.getValue();
                if (units == null) continue;
                Map<UnitKey, List<Integer>> byKey = new HashMap<>();
                for (MovementUnitCount muc : units) {
                    if (muc == null) continue;
                    UnitKey key = Units.getUnitKey(muc.unitType(), muc.colorID());
                    if (key == null) continue;
                    List<Integer> counts = muc.counts();
                    byKey.put(key, counts == null ? List.of(0, 0) : counts);
                }
                internal.put(normalizedKey, byKey);
            }
        }

        // Validate that active system is already set and matches targetPosition
        String active = game.getActiveSystem();
        if (active == null || active.isBlank()) {
            throw new IllegalStateException("Active system is not set. Activate a system first.");
        }
        if (targetPosition != null && !targetPosition.equals(active)) {
            throw new IllegalStateException(
                    "Target position '" + targetPosition + "' does not match active system '" + active + "'.");
        }

        // Stage the full displacement and remove units from origins
        TacticalActionDisplacementService.stageFullDisplacementAndRemoveFromOrigins(game, player, internal);

        Tile tile = game.getTileByPosition(active);
        if (tile == null) return null;

        // Post the movement UI with a "Done moving" button (no commit yet)
        TacticalActionOutputService.refreshButtonsAndMessageForChoosingTile(null, game, player);

        return tile;
    }

    private String normalizeUnitHolderKey(Game game, String raw) {
        if (raw == null) return null;
        int dash = raw.indexOf('-');
        if (dash <= 0) return raw;
        String pos = raw.substring(0, dash);
        String holder = raw.substring(dash + 1);
        // Normalize planet/unit-holder name (case-insensitive, alias-aware)
        String normalizedHolder = AliasHandler.resolvePlanet(holder);
        // Fallback to lower-case if alias handler did not map it
        if (normalizedHolder == null || normalizedHolder.isBlank()) normalizedHolder = holder;
        normalizedHolder = normalizedHolder.toLowerCase();
        return pos + "-" + normalizedHolder;
    }
}
