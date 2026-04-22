package ti4.contest.replay.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CombatReplayEventPayload.StartPayload.class, name = "START"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.RollPayload.class, name = "ROLL"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.HitAssignPayload.class, name = "HIT_ASSIGN"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.CardPayload.class, name = "CARD"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.AgentPayload.class, name = "AGENT"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.InfoPayload.class, name = "INFO"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.ResolvedPayload.class, name = "RESOLVED"),
    @JsonSubTypes.Type(value = CombatReplayEventPayload.CancelledPayload.class, name = "CANCELLED")
})
public interface CombatReplayEventPayload {

    @JsonTypeName("START")
    record StartPayload() implements CombatReplayEventPayload {}

    @JsonTypeName("ROLL")
    record RollPayload(
            String summaryHeader,
            List<String> modifierLines,
            List<String> unitRollLines,
            String totalHitsLine,
            List<String> specialLines)
            implements CombatReplayEventPayload {}

    @JsonTypeName("HIT_ASSIGN")
    record HitAssignPayload(String tilePosition, String combatStateSnapshotJson) implements CombatReplayEventPayload {}

    @JsonTypeName("CARD")
    record CardPayload(String componentId) implements CombatReplayEventPayload {}

    @JsonTypeName("AGENT")
    record AgentPayload(String componentId) implements CombatReplayEventPayload {}

    @JsonTypeName("INFO")
    record InfoPayload(String componentId) implements CombatReplayEventPayload {}

    @JsonTypeName("RESOLVED")
    record ResolvedPayload() implements CombatReplayEventPayload {}

    @JsonTypeName("CANCELLED")
    record CancelledPayload() implements CombatReplayEventPayload {}
}
