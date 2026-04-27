package ti4.contest.replay.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.service.CombatReplayContestLifecycleService;
import ti4.contest.replay.service.CombatReplayService;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/contest/replay")
/**
 * Exposes lightweight admin/debug endpoints for inspecting replay observations, candidates, events, and contests.
 */
public class CombatReplayDebugController {

    private static final JsonMapper MAPPER = JsonMapperManager.basic()
            .rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatContestSettings settings;
    private final ReplayDispatchSerializer payloadSerializer;
    private final CombatReplayContestLifecycleService contestLifecycleService;
    private final CombatReplayService combatReplayService;

    @GetMapping(value = "/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTopCandidates() {
        List<CandidateSummary> candidates =
                candidateRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startedAt"))).stream()
                        .map(this::toCandidateSummary)
                        .toList();
        return ok(new CandidateListResponse(runtimeState(), candidates));
    }

    @GetMapping(value = "/runtime", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getRuntimeState() {
        return ok(runtimeState());
    }

    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSettings() {
        return ok(settings);
    }

    @GetMapping(value = "/selection", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSelectionSnapshot() {
        return ok(new SelectionResponse(runtimeState(), combatReplayService.getSelectionDebugView()));
    }

    @GetMapping(value = "/candidates/{candidateId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getCandidate(@PathVariable Long candidateId) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) return ResponseEntity.notFound().build();
        return ok(buildCandidateDetailResponse(candidate));
    }

    @GetMapping(value = "/candidates/{candidateId}/promote", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> forcePromoteCandidate(@PathVariable Long candidateId) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) return ResponseEntity.notFound().build();

        CombatReplayContestLifecycleService.ForcePromoteResult result =
                contestLifecycleService.forcePromoteCandidate(candidateId);
        CombatReplayContestEntity contest = result.contest() != null
                ? result.contest()
                : replayContestRepository.findByCandidateId(candidateId).orElse(null);
        return ok(new ForcePromoteResponse(
                runtimeState(),
                result.promoted(),
                result.reason(),
                candidate,
                observationRepository.findById(candidate.getObservationId()).orElse(null),
                contest,
                loadEvents(candidateId)));
    }

    @GetMapping(value = "/contests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getLatestContests() {
        List<ContestSummary> contests =
                replayContestRepository
                        .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "postedAt")))
                        .stream()
                        .map(this::toContestSummary)
                        .toList();
        return ok(new ContestListResponse(runtimeState(), contests));
    }

    @GetMapping(value = "/contests/{contestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getContest(@PathVariable Long contestId) {
        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        if (contest == null) return ResponseEntity.notFound().build();
        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        return ok(new ContestDetailResponse(
                runtimeState(),
                contest,
                candidate,
                candidate == null
                        ? null
                        : observationRepository
                                .findById(candidate.getObservationId())
                                .orElse(null),
                candidate == null ? List.of() : loadEvents(candidate.getId())));
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok(writeJson(body));
    }

    private String writeJson(Object body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize replay debug response.", e);
        }
    }

    private RuntimeStateResponse runtimeState() {
        return new RuntimeStateResponse(
                settings.getPromotion().isEnabled(),
                settings.getRuntime().isDevMode(),
                settings.getRuntime().isDiscordPostingEnabled(),
                settings.getRuntime().isTrackAllCombatsAsCandidates(),
                settings.getRuntime().isImmediatePromotionOnResolve());
    }

    private CandidateSummary toCandidateSummary(CombatCandidateEntity candidate) {
        return new CandidateSummary(
                candidate,
                observationRepository.findById(candidate.getObservationId()).orElse(null),
                replayContestRepository.findByCandidateId(candidate.getId()).orElse(null),
                eventCount(candidate.getId()));
    }

    private ContestSummary toContestSummary(CombatReplayContestEntity contest) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        long eventCount = candidate == null ? 0 : eventCount(candidate.getId());
        return new ContestSummary(contest, candidate, eventCount);
    }

    private CandidateDetailResponse buildCandidateDetailResponse(CombatCandidateEntity candidate) {
        return new CandidateDetailResponse(
                runtimeState(),
                candidate,
                observationRepository.findById(candidate.getObservationId()).orElse(null),
                replayContestRepository.findByCandidateId(candidate.getId()).orElse(null),
                loadEvents(candidate.getId()));
    }

    private List<EventResponse> loadEvents(Long candidateId) {
        return candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidateId).stream()
                .map(this::toEventResponse)
                .toList();
    }

    private long eventCount(Long candidateId) {
        return candidateEventRepository
                .findByCandidateIdOrderBySequenceNumberAsc(candidateId)
                .size();
    }

    private EventResponse toEventResponse(CombatCandidateEventEntity event) {
        return new EventResponse(event, payloadSerializer.read(event));
    }

    private record RuntimeStateResponse(
            boolean promotionEnabled,
            boolean devMode,
            boolean discordPostingEnabled,
            boolean trackAllCombatsAsCandidates,
            boolean immediatePromotionOnResolve) {}

    private record CandidateListResponse(RuntimeStateResponse runtime, List<CandidateSummary> candidates) {}

    private record SelectionResponse(RuntimeStateResponse runtime, CombatReplayService.SelectionDebugView selection) {}

    private record CandidateSummary(
            CombatCandidateEntity candidate,
            CombatObservationEntity observation,
            CombatReplayContestEntity contest,
            long eventCount) {}

    private record CandidateDetailResponse(
            RuntimeStateResponse runtime,
            CombatCandidateEntity candidate,
            CombatObservationEntity observation,
            CombatReplayContestEntity contest,
            List<EventResponse> events) {}

    private record ForcePromoteResponse(
            RuntimeStateResponse runtime,
            boolean promoted,
            String reason,
            CombatCandidateEntity candidate,
            CombatObservationEntity observation,
            CombatReplayContestEntity contest,
            List<EventResponse> events) {}

    private record ContestListResponse(RuntimeStateResponse runtime, List<ContestSummary> contests) {}

    private record ContestSummary(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate, long eventCount) {}

    private record ContestDetailResponse(
            RuntimeStateResponse runtime,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            CombatObservationEntity observation,
            List<EventResponse> events) {}

    private record EventResponse(CombatCandidateEventEntity event, Object payload) {}
}
