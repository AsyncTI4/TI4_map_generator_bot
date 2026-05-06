package ti4.contest.replay.house.mentak;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.service.CombatReplayHouseScoringContext;
import ti4.contest.replay.service.CombatReplayHouseScoringContext.HousePrediction;
import ti4.contest.replay.service.CombatReplayHouseScoringRule;

@Service
@Order(20)
@RequiredArgsConstructor
public class CombatReplayMentakScoringRule implements CombatReplayHouseScoringRule {

    private static final int PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS = 4;
    private static final int PROMISE_OF_PROTECTION_MULTIPLIER = 5;

    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayHouseAbilityUseRepository abilityUseRepository;

    @Override
    public void apply(CombatReplayHouseScoringContext context) {
        int points = 0;
        for (HousePrediction prediction : context.predictions()) {
            CombatReplayHouse house = context.houseForUser(prediction.discordUserId());
            if (house != null && house != CombatReplayHouse.MENTAK && !prediction.correct()) {
                points += PILLAGE_POINTS_PER_OTHER_DELEGATION_MISS;
            }
        }
        context.addAbilitySummary(CombatReplayHouse.MENTAK, "Pillage", points, true);
        applyPromiseOfProtection(context);
    }

    private void applyPromiseOfProtection(CombatReplayHouseScoringContext context) {
        if (context.contest() == null || context.contest().getCandidateId() == null) return;

        CombatCandidateEntity candidate =
                candidateRepository.findById(context.contest().getCandidateId()).orElse(null);
        if (candidate == null || candidate.getWinnerFaction() == null) return;

        CombatReplayDecoys.Abilities abilities = CombatReplayDecoys.read(candidate.getReplayAbilitiesJson());
        if (!abilities.hasDecoys()) return;
        boolean decoyedWinner = abilities.decoy().units().stream()
                .anyMatch(unit -> context.normalizeFaction(unit.faction())
                        .equals(context.normalizeFaction(candidate.getWinnerFaction())));
        if (!decoyedWinner) return;

        List<CombatReplayHouseAbilityUseEntity> uses =
                abilityUseRepository.findByCandidateIdAndHouse(candidate.getId(), CombatReplayHouse.MENTAK);
        int favorCost = uses.stream()
                .map(CombatReplayHouseAbilityUseEntity::getFavorCost)
                .filter(cost -> cost != null && cost > 0)
                .findFirst()
                .orElse(0);
        if (favorCost <= 0) return;

        context.addAbilitySummary(
                CombatReplayHouse.MENTAK, "Promise of Protection", favorCost * PROMISE_OF_PROTECTION_MULTIPLIER);
    }
}
