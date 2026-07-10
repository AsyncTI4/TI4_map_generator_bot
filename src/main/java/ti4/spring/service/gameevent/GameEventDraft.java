package ti4.spring.service.gameevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Units.UnitKey;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.website.model.CompactMovementState;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/**
 * Undo-safe, strictly ordered draft of tactical sub-events. The draft lives as ONE JSON line in the game save file via
 * {@link Game#getPendingSubEventsJson()} so that undo (which restores the save file byte-for-byte) rolls it back for
 * free. This utility operates ONLY on that field and never touches the database.
 *
 * <p>Serialization goes through an explicit {@code List<GameSubEvent>} type so Jackson emits the polymorphic
 * {@code type} discriminator; serializing the list via a raw {@code Object}/collection reference drops it.
 */
@UtilityClass
public class GameEventDraft {

    private static final TypeReference<List<GameSubEvent>> LIST_TYPE = new TypeReference<>() {};

    public static void open(Game game) {
        game.setPendingSubEventsJson("[]");
        game.setPendingMovementState("");
    }

    public static void clear(Game game) {
        game.setPendingSubEventsJson("");
        game.setPendingMovementState("");
    }

    public static boolean isOpen(Game game) {
        return StringUtils.isNotBlank(game.getPendingSubEventsJson());
    }

    /**
     * Appends a sub-event to the draft. Returns false (no-op) when the draft is not open or on parse error; callers use
     * the return value to decide whether to fall back to a top-level event.
     */
    public static boolean stage(Game game, GameSubEvent subEvent) {
        if (!isOpen(game)) return false;
        try {
            List<GameSubEvent> subEvents = parse(game.getPendingSubEventsJson());
            subEvents.add(subEvent);
            game.setPendingSubEventsJson(serialize(subEvents));
            return true;
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to stage tactical sub-event.", e);
            return false;
        }
    }

    /** Returns the staged sub-events (empty when closed/empty) and clears the draft. */
    public static List<GameSubEvent> drain(Game game) {
        List<GameSubEvent> subEvents;
        try {
            subEvents = isOpen(game) ? parse(game.getPendingSubEventsJson()) : new ArrayList<>();
        } catch (Exception e) {
            // An unparseable draft (e.g. staged by a newer bot version, then rolled back) must not break
            // the tactical action's end buttons; the text summary still carries the information.
            BotLogger.error(new LogOrigin(game), "Failed to drain tactical sub-event draft.", e);
            subEvents = new ArrayList<>();
        }
        game.setPendingSubEventsJson("");
        return subEvents;
    }

    /** Captures committed displacement while the enclosing tactical-event draft remains undo-safe. */
    public static boolean stageMovement(
            Game game, String targetPosition, Map<String, Map<UnitKey, List<Integer>>> displacement) {
        if (!isOpen(game) || !hasMovedUnits(displacement)) return false;
        try {
            game.setPendingMovementState(CompactMovementState.serialize(targetPosition, displacement));
            return true;
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to stage tactical movement state.", e);
            return false;
        }
    }

    /** Returns the committed movement payload, if any, and clears only that portion of the draft. */
    public static String drainMovement(Game game) {
        String movementState = StringUtils.trimToNull(game.getPendingMovementState());
        game.setPendingMovementState("");
        return movementState;
    }

    private static boolean hasMovedUnits(Map<String, Map<UnitKey, List<Integer>>> displacement) {
        return displacement != null
                && displacement.values().stream()
                        .flatMap(units -> units.values().stream())
                        .filter(states -> states != null)
                        .flatMap(List::stream)
                        .anyMatch(count -> count != null && count > 0);
    }

    /** Compact JSON (one line, {@code type} discriminators intact) for a list of sub-events. */
    public static String serialize(List<GameSubEvent> subEvents) {
        return JsonMapperManager.basic().writerFor(LIST_TYPE).writeValueAsString(subEvents);
    }

    /**
     * Sub-events as a pre-typed JsonNode for embedding in an Object-valued event payload; serializing the raw list
     * there would drop the polymorphic {@code type} discriminator that the frontend relies on.
     */
    public static JsonNode toJsonNode(List<GameSubEvent> subEvents) {
        return JsonMapperManager.basic().readTree(serialize(subEvents));
    }

    private static List<GameSubEvent> parse(String json) {
        return JsonMapperManager.basic().readValue(json, LIST_TYPE);
    }
}
