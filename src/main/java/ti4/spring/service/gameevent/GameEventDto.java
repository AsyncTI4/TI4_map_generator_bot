package ti4.spring.service.gameevent;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

public record GameEventDto(
        long seq,
        String archetype,
        int round,
        String phase,
        String faction,
        long timestamp,
        JsonNode payload,
        @JsonInclude(JsonInclude.Include.NON_NULL) String mapState) {}
