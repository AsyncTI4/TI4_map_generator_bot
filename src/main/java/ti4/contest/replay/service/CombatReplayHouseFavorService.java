package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    private static final long DEBUG_FAVOR_BALANCE_CONTEST_ID = 0L;

    private final CombatReplayHouseScoreRepository houseScoreRepository;
    private final CombatReplayHouseAbilityUseRepository houseAbilityUseRepository;

    public int balance(CombatReplayHouse house) {
        return ledger(house).balance();
    }

    public FavorLedger ledger(CombatReplayHouse house) {
        if (house == null) return new FavorLedger(0, 0, 0);

        int earned = 0;
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByHouse(house)) {
            earned += safeInt(score.getFavorPoints());
        }

        int spent = 0;
        for (CombatReplayHouseAbilityUseEntity use : houseAbilityUseRepository.findByHouse(house)) {
            spent += safeInt(use.getFavorCost());
        }

        return new FavorLedger(earned, spent, Math.max(0, earned - spent));
    }

    public FavorLedger ledgerWithContestFavor(CombatReplayHouse house, Long contestId, int contestFavor) {
        if (house == null) return new FavorLedger(0, 0, 0);

        int earned = Math.max(0, contestFavor);
        for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByHouse(house)) {
            if (contestId != null && contestId.equals(score.getContestId())) continue;
            earned += safeInt(score.getFavorPoints());
        }

        int spent = 0;
        for (CombatReplayHouseAbilityUseEntity use : houseAbilityUseRepository.findByHouse(house)) {
            spent += safeInt(use.getFavorCost());
        }

        return new FavorLedger(earned, spent, Math.max(0, earned - spent));
    }

    public boolean canAfford(CombatReplayHouse house, int favorCost) {
        return Math.max(0, favorCost) <= balance(house);
    }

    public int spentForContest(CombatReplayHouse house, Long candidateId) {
        if (house == null || candidateId == null) return 0;

        int spent = 0;
        for (CombatReplayHouseAbilityUseEntity use :
                houseAbilityUseRepository.findByCandidateIdAndHouse(candidateId, house)) {
            spent += safeInt(use.getFavorCost());
        }
        return spent;
    }

    public void setAllBalancesForDebug(int targetBalance) {
        int desiredBalance = Math.max(0, targetBalance);
        Map<CombatReplayHouse, CombatReplayHouseScoreEntity> debugScoresByHouse =
                new EnumMap<>(CombatReplayHouse.class);
        for (CombatReplayHouseScoreEntity score :
                houseScoreRepository.findByContestId(DEBUG_FAVOR_BALANCE_CONTEST_ID)) {
            debugScoresByHouse.put(score.getHouse(), score);
        }

        List<CombatReplayHouseScoreEntity> scores = new ArrayList<>();
        for (CombatReplayHouse house : CombatReplayHouse.assignmentOrder()) {
            int earnedOutsideDebugScore = 0;
            for (CombatReplayHouseScoreEntity score : houseScoreRepository.findByHouse(house)) {
                if (DEBUG_FAVOR_BALANCE_CONTEST_ID == safeLong(score.getContestId())) continue;
                earnedOutsideDebugScore += safeInt(score.getFavorPoints());
            }

            int spent = 0;
            for (CombatReplayHouseAbilityUseEntity use : houseAbilityUseRepository.findByHouse(house)) {
                spent += safeInt(use.getFavorCost());
            }

            CombatReplayHouseScoreEntity score =
                    debugScoresByHouse.getOrDefault(house, new CombatReplayHouseScoreEntity());
            score.setContestId(DEBUG_FAVOR_BALANCE_CONTEST_ID);
            score.setHouse(house);
            score.setPredictionPoints(safeInt(score.getPredictionPoints()));
            score.setSideBetPoints(safeInt(score.getSideBetPoints()));
            score.setAbilityPoints(safeInt(score.getAbilityPoints()));
            score.setFavorPoints(desiredBalance + spent - earnedOutsideDebugScore);
            score.setTotalPoints(safeInt(score.getTotalPoints()));
            score.setPredictionCount(safeInt(score.getPredictionCount()));
            score.setCorrectPredictions(safeInt(score.getCorrectPredictions()));
            score.setAbilityBreakdownJson(
                    score.getAbilityBreakdownJson() == null ? "[]" : score.getAbilityBreakdownJson());
            score.setScoredAt(LocalDateTime.now());
            scores.add(score);
        }
        houseScoreRepository.saveAllAndFlush(scores);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? Long.MIN_VALUE : value;
    }

    public record FavorLedger(int earned, int spent, int balance) {}
}
