package ti4.contest.replay.core;

import org.springframework.stereotype.Component;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.json.JsonMapperManager;

@Component
public class CombatReplayEventPayloadSerde {

    public String write(CombatReplayEventPayload payload) {
        if (payload == null) return null;
        try {
            return JsonMapperManager.basic().writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    public CombatReplayEventPayload read(CombatCandidateEventEntity event) {
        if (event == null
                || event.getPayloadJson() == null
                || event.getPayloadJson().isBlank()) return null;
        return read(event.getPayloadJson());
    }

    public CombatReplayEventPayload read(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            return JsonMapperManager.basic().readValue(payloadJson, CombatReplayEventPayload.class);
        } catch (Exception e) {
            return null;
        }
    }
}
