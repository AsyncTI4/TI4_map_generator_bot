package ti4.spring.api.publicstatus;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.json.JsonMapperManager;
import ti4.spring.service.contest.CombatContestSelectionService;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/contest")
public class CombatContestSelectionController {

    private static final JsonMapper MAPPER = JsonMapperManager.basic()
            .rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final CombatContestSelectionService combatContestSelectionService;

    @SneakyThrows
    @GetMapping(value = "/selection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSelection() {
        CombatContestSelectionService.Snapshot snapshot =
                combatContestSelectionService.recomputeAndGetSelectionSnapshot();
        return ResponseEntity.ok(MAPPER.writeValueAsString(snapshot));
    }
}
