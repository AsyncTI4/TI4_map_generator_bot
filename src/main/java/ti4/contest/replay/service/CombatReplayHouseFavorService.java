package ti4.contest.replay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.entities.CombatReplayHouseScoreEntity;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseScoreRepository;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseFavorService {

    private final CombatReplayHouseScoreRepository houseScoreRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;

    public int balance(CombatReplayHouse house) {
        if (house == null) return 0;

        int earned = 0;
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByHouse(house)) {
            earned += safeInt(score.getFavorPoints());
        }

        int spent = 0;
        for (CombatReplayHouseAbilityUseEntity use : houseAbilityUseRepository.findByHouse(house)) {
            spent += safeInt(use.getFavorCost());
        }

        return Math.max(0, earned - spent);
    }

    public boolean canAfford(CombatReplayHouse house, int favorCost) {
        return Math.max(0, favorCost) <= balance(house);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
