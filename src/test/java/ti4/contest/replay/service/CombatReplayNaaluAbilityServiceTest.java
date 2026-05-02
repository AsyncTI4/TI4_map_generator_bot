package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.naalu.CombatReplayNaaluAbilityService;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityVoteRepository;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.json.JsonMapperManager;
import ti4.service.combat.CombatRollType;

class CombatReplayNaaluAbilityServiceTest {

    private final CombatReplayContestRepository contestRepository = mock(CombatReplayContestRepository.class);
    private final CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
    private final CombatCandidateEventRepository eventRepository = mock(CombatCandidateEventRepository.class);
    private final CombatReplayHouseAbilityUseRepository abilityUseRepository =
            mock(CombatReplayHouseAbilityUseRepository.class);
    private final CombatReplayHouseAbilityVoteRepository abilityVoteRepository =
            mock(CombatReplayHouseAbilityVoteRepository.class);
    private final ReplayDispatchSerializer serializer = new ReplayDispatchSerializer();
    private final CombatReplayNaaluAbilityService service = new CombatReplayNaaluAbilityService(
            new CombatContestSettings(),
            contestRepository,
            candidateRepository,
            eventRepository,
            abilityUseRepository,
            abilityVoteRepository,
            mock(CombatReplayHouseFavorService.class),
            mock(CombatReplayHouseService.class),
            serializer);

    @Test
    void actionCardPeekAttributesCardsToActors() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity event =
                event(CombatCandidateEventType.INFO, null, "naalu", ReplayDispatchPayload.actionCardPlayed("abs"));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(event));

        String peek = service.renderActionCardPeek(1L);

        assertTrue(peek.contains("naalu: _Ancient Burial Sites_"));
    }

    @Test
    void roundOneRollPeekAppliesReplayDecoys() throws Exception {
        CombatCandidateEntity candidate = candidate();
        candidate.setReplayAbilitiesJson(JsonMapperManager.basic()
                .writeValueAsString(new CombatReplayDecoys.Abilities(
                        new CombatReplayDecoys.Decoy(List.of(new CombatReplayDecoys.DecoyUnit(
                                "naalu", ":naalu:", "blu", UnitType.Destroyer, Constants.SPACE, 1))))));
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity event =
                event(CombatCandidateEventType.ROLL, 1, "naalu", ReplayDispatchPayload.combatRoll("", rollPayload()));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(event));

        String peek = service.renderRoundOneRollPeek(1L);

        assertTrue(peek.contains("`2x`"));
    }

    @Test
    void useNaaluPeekRejectsAfterPredictionsLockWithoutClaiming() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        contest.setReplayStartAt(LocalDateTime.now().minusSeconds(1));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));

        CombatReplayNaaluAbilityService.VoteResult result = service.voteActionCardPeek(1L, "user-id", "user-name");

        assertFalse(result.accepted());
        assertEquals("The Naalu Gift of Foresight window is closed for this combat.", result.message());
        verifyNoInteractions(abilityUseRepository);
    }

    private CombatReplayContestEntity contest(CombatCandidateEntity candidate) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(1L);
        contest.setCandidateId(candidate.getId());
        return contest;
    }

    private CombatCandidateEntity candidate() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        candidate.setGameName("missing-test-game");
        candidate.setAttackerFaction("naalu");
        candidate.setDefenderFaction("hacan");
        return candidate;
    }

    private CombatCandidateEventEntity event(
            CombatCandidateEventType eventType, Integer round, String actorFaction, ReplayDispatchPayload payload) {
        CombatCandidateEventEntity event = new CombatCandidateEventEntity();
        event.setCandidateId(7L);
        event.setOccurredAt(LocalDateTime.now());
        event.setSequenceNumber(1);
        event.setEventType(eventType);
        event.setRoundNumber(round);
        event.setActorFaction(actorFaction);
        event.setSummaryText("summary");
        event.setPayloadJson(serializer.write(payload));
        return event;
    }

    private CombatRollPayload rollPayload() {
        return new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        "naalu",
                        "blue",
                        ":naalu:",
                        "hacan",
                        "red",
                        "101",
                        "18",
                        Constants.SPACE,
                        "space combat",
                        CombatRollType.combatround,
                        1,
                        false,
                        false),
                List.of(),
                List.of(),
                List.of(new CombatRollPayload.UnitRoll(
                        "destroyer",
                        "dd",
                        "destroyer",
                        "Destroyer",
                        "Destroyer",
                        ":destroyer:",
                        1,
                        1,
                        0,
                        9,
                        0,
                        9,
                        CombatRollPayload.RollSegmentType.PRIMARY,
                        List.of(new CombatRollPayload.DieRoll(4, 9, false, CombatRollPayload.DieRollSource.PRIMARY)),
                        0)),
                new CombatRollPayload.RollTotal(1, 0, 1, 1));
    }
}
