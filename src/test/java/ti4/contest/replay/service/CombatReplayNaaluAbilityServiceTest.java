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
    private final ReplayDispatchSerializer serializer = new ReplayDispatchSerializer();
    private final CombatReplayNaaluAbilityService service = new CombatReplayNaaluAbilityService(
            new CombatContestSettings(),
            contestRepository,
            candidateRepository,
            eventRepository,
            abilityUseRepository,
            mock(CombatReplayHouseFavorService.class),
            mock(CombatReplayHouseAbilityVoteService.class),
            mock(CombatReplayHousePhaseService.class),
            mock(CombatReplayHouseService.class),
            serializer);

    @Test
    void actionCardPeekAttributesCardsToFactions() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity event =
                event(CombatCandidateEventType.INFO, null, "naalu", ReplayDispatchPayload.actionCardPlayed("abs"));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(event));

        String peek = service.renderActionCardPeek(1L);

        assertTrue(peek.contains("Naalu: _Ancient Burial Sites_") || peek.contains("Naalu: _abs_"));
        assertFalse(peek.contains("user-name"));
    }

    @Test
    void roundOneRollPeekOnlyShowsTotalHits() throws Exception {
        CombatCandidateEntity candidate = candidate();
        candidate.setReplayAbilitiesJson(JsonMapperManager.basic()
                .writeValueAsString(new CombatReplayDecoys.Abilities(
                        new CombatReplayDecoys.Decoy(List.of(new CombatReplayDecoys.DecoyUnit(
                                "naalu", ":naalu:", "blue", UnitType.Destroyer, Constants.SPACE, 1))))));
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity event =
                event(CombatCandidateEventType.ROLL, 1, "naalu", ReplayDispatchPayload.combatRoll("", rollPayload()));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(event));

        String peek = service.renderRoundOneRollPeek(1L);

        assertTrue(peek.contains("###"));
        assertTrue(peek.contains("Naalu"));
        assertTrue(peek.contains("**Total hits 0**"));
        assertFalse(peek.contains("user-name"));
        assertFalse(peek.contains("`2x`"));
        assertFalse(peek.contains("Destroyer"));
        assertFalse(peek.contains("hits on"));
    }

    @Test
    void luckOmensBucketAllRollsWithoutExposingExpectedValues() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity attackerRoll = event(
                CombatCandidateEventType.ROLL,
                1,
                "naalu",
                ReplayDispatchPayload.combatRoll("", luckPayload("naalu", true, true, true, true, true)));
        CombatCandidateEventEntity defenderRoll = event(
                CombatCandidateEventType.ROLL,
                1,
                "hacan",
                ReplayDispatchPayload.combatRoll(
                        "",
                        luckPayload("hacan", false, false, false, false, false, false, false, false, false, false)));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(attackerRoll, defenderRoll));

        String omens = service.renderLuckOmens(1L);

        assertTrue(omens.contains("Overall: **Lucky**"));
        assertTrue(omens.contains("Naalu: **Lucky**"));
        assertTrue(omens.contains("Hacan: **Unlucky**"));
        assertFalse(omens.contains("user-name"));
        assertFalse(omens.contains("Expected"));
        assertFalse(omens.contains("EV"));
    }

    @Test
    void luckOmensUsesAverageBand() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        CombatCandidateEventEntity event = event(
                CombatCandidateEventType.ROLL,
                1,
                "naalu",
                ReplayDispatchPayload.combatRoll("", luckPayload("naalu", true, false, false, false, false)));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(event));

        String omens = service.renderLuckOmens(1L);

        assertTrue(omens.contains("Overall: **Average**"));
        assertTrue(omens.contains("Naalu: **Average**"));
        assertFalse(omens.contains("user-name"));
    }

    @Test
    void useNaaluPeekRejectsAfterPredictionsLockWithoutClaiming() {
        CombatCandidateEntity candidate = candidate();
        CombatReplayContestEntity contest = contest(candidate);
        contest.setReplayStartAt(LocalDateTime.now().minusSeconds(1));
        when(contestRepository.findById(1L)).thenReturn(Optional.of(contest));

        CombatReplayInteractionResult result = service.voteActionCardPeek(1L, "user-id", "user-name");

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

    private CombatRollPayload luckPayload(String actorFaction, boolean... successes) {
        List<CombatRollPayload.DieRoll> dice = new java.util.ArrayList<>();
        for (boolean success : successes) {
            dice.add(new CombatRollPayload.DieRoll(
                    success ? 9 : 4, 9, success, CombatRollPayload.DieRollSource.PRIMARY));
        }
        return new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        actorFaction,
                        "blue",
                        ":" + actorFaction + ":",
                        "opponent",
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
                        successes.length,
                        0,
                        9,
                        0,
                        9,
                        CombatRollPayload.RollSegmentType.PRIMARY,
                        dice,
                        (int) dice.stream()
                                .filter(CombatRollPayload.DieRoll::success)
                                .count())),
                new CombatRollPayload.RollTotal(successes.length, 0, successes.length, successes.length));
    }
}
