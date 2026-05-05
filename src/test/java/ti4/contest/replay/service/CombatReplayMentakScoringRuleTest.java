package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.house.mentak.CombatReplayMentakScoringRule;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;

class CombatReplayMentakScoringRuleTest {

    private final CombatCandidateRepository candidateRepository = mock(CombatCandidateRepository.class);
    private final CombatReplayHouseAbilityUseRepository abilityUseRepository =
            mock(CombatReplayHouseAbilityUseRepository.class);
    private final CombatReplayMentakScoringRule rule =
            new CombatReplayMentakScoringRule(candidateRepository, abilityUseRepository);

    @Test
    void promiseOfProtectionPaysFiveTimesFalseColorsCostWhenDecoyedFactionWins() {
        CombatCandidateEntity candidate = candidate("ghost", "ghost", 40);
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(abilityUseRepository.findByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.MENTAK))
                .thenReturn(List.of(use(40)));
        EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseAbilitySummary>> abilities =
                emptyAbilities();

        rule.apply(context(candidate, abilities));

        assertTrue(abilities.get(CombatReplayHouse.MENTAK).stream()
                .anyMatch(summary -> "Promise of Protection".equals(summary.label()) && summary.points() == 200));
    }

    @Test
    void promiseOfProtectionDoesNotPayWhenDecoyedFactionLoses() {
        CombatCandidateEntity candidate = candidate("ghost", "bastion", 40);
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(abilityUseRepository.findByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.MENTAK))
                .thenReturn(List.of(use(40)));
        EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseAbilitySummary>> abilities =
                emptyAbilities();

        rule.apply(context(candidate, abilities));

        assertFalse(abilities.get(CombatReplayHouse.MENTAK).stream()
                .anyMatch(summary -> "Promise of Protection".equals(summary.label())));
    }

    private CombatCandidateEntity candidate(String decoyFaction, String winnerFaction, int favorCost) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(7L);
        candidate.setWinnerFaction(winnerFaction);
        candidate.setReplayAbilitiesJson(CombatReplayDecoys.addDecoy(
                null,
                new CombatReplayDecoys.DecoyUnit(
                        decoyFaction, ":" + decoyFaction + ":", "blue", UnitType.Dreadnought, Constants.SPACE, 1)));
        return candidate;
    }

    private CombatReplayHouseAbilityUseEntity use(int favorCost) {
        CombatReplayHouseAbilityUseEntity use = new CombatReplayHouseAbilityUseEntity();
        use.setFavorCost(favorCost);
        return use;
    }

    private CombatReplayHouseScoringContext context(
            CombatCandidateEntity candidate,
            EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseAbilitySummary>> abilities) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setCandidateId(candidate.getId());
        return new CombatReplayHouseScoringContext(
                contest, List.of(), Set.of(), List.of(), List.of(), Map.of(), emptyTotals(), abilities, emptyFavors());
    }

    private EnumMap<CombatReplayHouse, CombatReplayHouseScoringContext.HouseTotals> emptyTotals() {
        EnumMap<CombatReplayHouse, CombatReplayHouseScoringContext.HouseTotals> totals =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            totals.put(house, new CombatReplayHouseScoringContext.HouseTotals());
        }
        return totals;
    }

    private EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseAbilitySummary>> emptyAbilities() {
        EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseAbilitySummary>> abilities =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            abilities.put(house, new java.util.ArrayList<>());
        }
        return abilities;
    }

    private EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseFavorSummary>> emptyFavors() {
        EnumMap<CombatReplayHouse, List<CombatReplayHouseLedgerService.HouseFavorSummary>> favors =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            favors.put(house, new java.util.ArrayList<>());
        }
        return favors;
    }
}
