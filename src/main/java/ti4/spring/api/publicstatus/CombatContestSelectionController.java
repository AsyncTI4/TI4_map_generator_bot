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
        return ResponseEntity.ok(MAPPER.writeValueAsString(SelectionResponse.from(snapshot)));
    }

    private record SelectionResponse(Metadata metadata, LiveWindow liveWindow, Thresholds thresholds, Tuning tuning) {

        static SelectionResponse from(CombatContestSelectionService.Snapshot snapshot) {
            CombatContestSelectionService.Settings settings = snapshot.settings();
            return new SelectionResponse(
                    new Metadata(settings.updatedAt(), snapshot.generatedAt(), settings.selectionMode()),
                    new LiveWindow(
                            settings.lookbackMinutes(),
                            settings.windowSampleCount(),
                            snapshot.sampleCountLastHour(),
                            snapshot.observedCombatsPerHour()),
                    new Thresholds(
                            settings.combatSizeCutoff(),
                            settings.combatSizePercentile(),
                            settings.fairnessFloor(),
                            settings.fairnessPercentile(),
                            settings.averageFairness()),
                    new Tuning(
                            settings.targetPostsPerHour(),
                            settings.targetSelectionFraction(),
                            settings.cooldownMinutes(),
                            settings.minimumSampleCount()));
        }
    }

    private record Metadata(
            java.time.LocalDateTime settingsUpdatedAt, java.time.LocalDateTime generatedAt, String selectionMode) {}

    private record LiveWindow(
            int lookbackMinutes,
            int configuredWindowSampleCount,
            int sampleCountLastHour,
            double observedCombatsPerHour) {}

    private record Thresholds(
            double combatSizeCutoff,
            double combatSizePercentile,
            double fairnessFloor,
            double fairnessPercentile,
            double averageFairness) {}

    private record Tuning(
            double targetPostsPerHour, double targetSelectionFraction, int cooldownMinutes, int minimumSampleCount) {}
}
