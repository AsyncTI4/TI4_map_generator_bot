package ti4.spring.service.gameevent;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * Structured sub-event collected during a tactical action and embedded in the TACTICAL_ACTION event payload.
 *
 * <p>The {@code type} discriminator values are EXACTLY the {@link GameSubEventType} constant names; this is the wire
 * contract with the React frontend and must not drift.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GameSubEvent.Combat.class, name = "COMBAT"),
    @JsonSubTypes.Type(value = GameSubEvent.ControlEstablished.class, name = "CONTROL_ESTABLISHED"),
    @JsonSubTypes.Type(value = GameSubEvent.ActionCardPlayed.class, name = "ACTION_CARD_PLAYED"),
    @JsonSubTypes.Type(value = GameSubEvent.LeaderPlayed.class, name = "LEADER_PLAYED"),
    @JsonSubTypes.Type(value = GameSubEvent.TechExhausted.class, name = "TECH_EXHAUSTED"),
    @JsonSubTypes.Type(value = GameSubEvent.Production.class, name = "PRODUCTION"),
    @JsonSubTypes.Type(value = GameSubEvent.ManualCommand.class, name = "MANUAL_COMMAND")
})
public sealed interface GameSubEvent {
    record Combat(String kind, String tile, String planet, String vsFaction) implements GameSubEvent {}

    record ControlEstablished(String planet) implements GameSubEvent {}

    record ActionCardPlayed(String faction, String cardId, String cardName) implements GameSubEvent {}

    record LeaderPlayed(String faction, String leaderType, String leaderId) implements GameSubEvent {}

    record TechExhausted(String faction, String techId) implements GameSubEvent {}

    record Production(String tile, Map<String, Integer> units, Integer cost) implements GameSubEvent {}

    record ManualCommand(String user, String command) implements GameSubEvent {}
}
