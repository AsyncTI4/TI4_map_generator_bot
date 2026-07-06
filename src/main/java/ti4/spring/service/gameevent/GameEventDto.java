package ti4.spring.service.gameevent;

import tools.jackson.databind.JsonNode;

public record GameEventDto(
        long seq, String archetype, int round, String phase, String faction, long timestamp, JsonNode payload) {}
