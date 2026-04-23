package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatReplayPromotionScoreSupport;
import ti4.contest.replay.core.CombatReplayRenderSnapshotSupport;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.entities.CombatReplayPromotionScoreBackfillEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.contest.replay.repository.CombatReplayPromotionScoreBackfillRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;

@Service
@RequiredArgsConstructor
public class CombatReplayPromotionScoreBackfillService {

    private static final String BACKFILL_KEY = "promotion_score_blowout_penalty_v1";

    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayPromotionScoreBackfillRepository backfillRepository;
    private final ReplayDispatchSerializer payloadSerializer;

    public void runBackfill() {
        CombatReplayPromotionScoreBackfillEntity existing =
                backfillRepository.findById(BACKFILL_KEY).orElse(null);
        if (existing != null) {
            if (existing.getCompletedAt() != null) {
                BotLogger.info("Combat replay promotion-score backfill already completed.");
            } else {
                BotLogger.info("Combat replay promotion-score backfill already started; refusing to rerun.");
            }
            return;
        }

        CombatReplayPromotionScoreBackfillEntity backfill = new CombatReplayPromotionScoreBackfillEntity();
        backfill.setBackfillKey(BACKFILL_KEY);
        backfill.setStartedAt(LocalDateTime.now());
        backfillRepository.save(backfill);

        int updated = 0;
        int skipped = 0;
        for (CombatCandidateEntity candidate : candidateRepository.findByStatus(CombatCandidateStatus.RESOLVED)) {
            if (recomputePromotionScore(candidate)) {
                updated++;
            } else {
                skipped++;
            }
        }

        backfill.setCompletedAt(LocalDateTime.now());
        backfillRepository.save(backfill);
        BotLogger.info("Combat replay promotion-score backfill completed. Updated=" + updated + ", skipped=" + skipped);
    }

    private boolean recomputePromotionScore(CombatCandidateEntity candidate) {
        CombatObservationEntity observation =
                observationRepository.findById(candidate.getObservationId()).orElse(null);
        if (observation == null) return false;

        String snapshotJson = extractLatestSnapshotJson(candidate.getId());
        if (snapshotJson == null || snapshotJson.isBlank()) return false;

        Game snapshotGame = CombatReplayRenderSnapshotSupport.restoreGame(snapshotJson);
        if (snapshotGame == null) return false;

        Player attacker = snapshotGame.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = snapshotGame.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Tile tile = snapshotGame.getTileByPosition(candidate.getTilePosition());
        UnitHolder space = tile == null ? null : tile.getUnitHolders().get(Constants.SPACE);
        if (attacker == null || defender == null || tile == null || space == null) return false;

        double attackerRemaining = LazaxCombatSupport.calculateFleetStrength(
                        snapshotGame, attacker, defender, tile, space)
                .value();
        double defenderRemaining = LazaxCombatSupport.calculateFleetStrength(
                        snapshotGame, defender, attacker, tile, space)
                .value();
        double attackerLossRatio = CombatReplayPromotionScoreSupport.computeLossRatio(
                observation.getAttackerStrength(), attackerRemaining);
        double defenderLossRatio = CombatReplayPromotionScoreSupport.computeLossRatio(
                observation.getDefenderStrength(), defenderRemaining);
        int roundsObserved = candidateEventRepository
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        candidate.setPromotionScore(CombatReplayPromotionScoreSupport.computePromotionScore(
                attackerLossRatio, defenderLossRatio, roundsObserved));
        candidateRepository.save(candidate);
        return true;
    }

    private String extractLatestSnapshotJson(Long candidateId) {
        List<CombatCandidateEventEntity> events =
                candidateEventRepository.findByCandidateIdOrderBySequenceNumberAsc(candidateId);
        Collections.reverse(events);
        for (CombatCandidateEventEntity event : events) {
            ReplayDispatchPayload payload = payloadSerializer.read(event);
            if (payload instanceof ReplayDispatchPayload.HitAssignDispatch hitAssign) {
                return hitAssign.combatStateSnapshotJson();
            }
            if (payload instanceof ReplayDispatchPayload.TileRenderMessageDispatch tileRenderMessage) {
                return tileRenderMessage.combatStateSnapshotJson();
            }
        }
        return null;
    }
}
