package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.RollSegmentType;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.service.combat.CombatRollType;

class CombatReplaySideBetPayoutServiceTest {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatCandidateEventRepository eventRepository = mock(CombatCandidateEventRepository.class);
    private final ReplayDispatchSerializer serializer = new ReplayDispatchSerializer();
    private final CombatReplaySideBetPayoutService service =
            new CombatReplaySideBetPayoutService(settings, eventRepository, serializer);

    @Test
    void usesFixedPayoutForExistingContestWithoutOddsModel() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();

        int payout = service.offeredPayout(contest, candidate(30, 30), CombatSideBetType.WINNER_ONE_HP, "sol");

        assertEquals(35, payout);
    }

    @Test
    void pricesRoundOneWhiffFromCapturedOpeningRollOdds() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = candidate();
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(rollEvent(candidate, "sol", payload(List.of(unitRoll(6, 2))))));

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(4, payout);
    }

    @Test
    void capsVeryLowOddsRoundOneSlamAtMaxPayout() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = candidate();
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(rollEvent(candidate, "sol", payload(List.of(unitRoll(9, 4))))));

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "sol");

        assertEquals(100, payout);
    }

    @Test
    void zeroProbabilityRoundOneSideBetUsesMaxPayout() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = candidate();
        when(eventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidate.getId()))
                .thenReturn(List.of(rollEvent(candidate, "sol", payload(List.of(unitRoll(1, 1))))));

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(100, payout);
    }

    @Test
    void pricesOneHpFromTotalInitialFightHp() {
        int smallFight = service.offeredPayout(oddsContest(), candidate(4, 4), CombatSideBetType.WINNER_ONE_HP, "sol");
        int largeFight =
                service.offeredPayout(oddsContest(), candidate(30, 30), CombatSideBetType.WINNER_ONE_HP, "sol");
        int hugeBlowout =
                service.offeredPayout(oddsContest(), candidate(60, 4), CombatSideBetType.WINNER_ONE_HP, "sol");

        assertEquals(3, smallFight);
        assertEquals(75, largeFight);
        assertEquals(76, hugeBlowout);
    }

    @Test
    void resolvesOldRowsWithLegacyFixedPayoutAndNewRowsWithLockedSnapshot() {
        CombatContestSideBetEntity legacy = new CombatContestSideBetEntity();
        legacy.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);

        CombatContestSideBetEntity locked = new CombatContestSideBetEntity();
        locked.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);
        locked.setOfferedProfitPoints(17);

        assertEquals(10, service.resolvedProfitPoints(legacy));
        assertEquals(17, service.resolvedProfitPoints(locked));
    }

    private CombatCandidateEntity candidate() {
        return candidate(null, null);
    }

    private CombatCandidateEntity candidate(Integer attackerHp, Integer defenderHp) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(100L);
        candidate.setAttackerFaction("sol");
        candidate.setDefenderFaction("yin");
        candidate.setAttackerHp(attackerHp == null ? null : attackerHp.doubleValue());
        candidate.setDefenderHp(defenderHp == null ? null : defenderHp.doubleValue());
        return candidate;
    }

    private CombatReplayContestEntity oddsContest() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setSideBetPayoutModel(CombatReplaySideBetPayoutService.ODDS_V1);
        return contest;
    }

    private CombatCandidateEventEntity rollEvent(
            CombatCandidateEntity candidate, String actorFaction, CombatRollPayload payload) {
        CombatCandidateEventEntity event = new CombatCandidateEventEntity();
        event.setCandidateId(candidate.getId());
        event.setEventType(CombatCandidateEventType.ROLL);
        event.setActorFaction(actorFaction);
        event.setRoundNumber(1);
        event.setPayloadJson(serializer.write(ReplayDispatchPayload.combatRoll("roll", payload)));
        return event;
    }

    private CombatRollPayload payload(List<CombatRollPayload.UnitRoll> unitRolls) {
        return new CombatRollPayload(
                new CombatRollPayload.RollHeader(
                        "sol",
                        "blue",
                        "",
                        "yin",
                        "red",
                        "101",
                        "18",
                        "space",
                        "space combat",
                        CombatRollType.combatround,
                        1,
                        false,
                        false),
                List.of(),
                List.of(),
                unitRolls,
                new CombatRollPayload.RollTotal(
                        unitRolls.stream().mapToInt(unit -> unit.dice().size()).sum(), 0, 0, 0));
    }

    private CombatRollPayload.UnitRoll unitRoll(int threshold, int diceCount) {
        return new CombatRollPayload.UnitRoll(
                "fighter",
                "ff",
                "fighter",
                "Fighter",
                "",
                "",
                diceCount,
                1,
                0,
                threshold,
                0,
                threshold,
                RollSegmentType.PRIMARY,
                java.util.stream.IntStream.range(0, diceCount)
                        .mapToObj(i -> new CombatRollPayload.DieRoll(1, threshold, false, DieRollSource.PRIMARY))
                        .toList(),
                0);
    }
}
