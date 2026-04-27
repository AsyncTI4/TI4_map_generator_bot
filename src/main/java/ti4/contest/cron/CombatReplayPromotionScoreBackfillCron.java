package ti4.contest.cron;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.contest.replay.service.CombatReplayService;
import ti4.cron.CronManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class CombatReplayPromotionScoreBackfillCron {

    private static final AtomicBoolean HAS_RUN = new AtomicBoolean(false);

    public static void register() {
        CronManager.scheduleOnce(
                CombatReplayPromotionScoreBackfillCron.class,
                CombatReplayPromotionScoreBackfillCron::runBackfill,
                0,
                TimeUnit.SECONDS);
    }

    private static void runBackfill() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running CombatReplayPromotionScoreBackfillCron.");
        try {
            runBackfillInternal();
        } catch (Exception e) {
            BotLogger.error("**CombatReplayPromotionScoreBackfillCron failed.**", e);
        }
        BotLogger.logCron("Finished CombatReplayPromotionScoreBackfillCron.");
    }

    private static void runBackfillInternal() {
        if (!HAS_RUN.compareAndSet(false, true)) {
            BotLogger.info("Combat replay promotion-score backfill already ran in this process.");
            return;
        }

        CombatCandidateRepository candidateRepository = SpringContext.getBean(CombatCandidateRepository.class);
        int updated = 0;
        int skipped = 0;
        for (CombatCandidateEntity candidate : candidateRepository.findByStatus(CombatCandidateStatus.RESOLVED)) {
            if (recomputePromotionScore(candidate)) {
                updated++;
            } else {
                skipped++;
            }
        }

        BotLogger.info("Combat replay promotion-score backfill completed. Updated=" + updated + ", skipped=" + skipped);
    }

    private static boolean recomputePromotionScore(CombatCandidateEntity candidate) {
        CombatObservationEntity observation = SpringContext.getBean(CombatObservationRepository.class)
                .findById(candidate.getObservationId())
                .orElse(null);
        if (observation == null) return false;

        String snapshotJson = extractLatestSnapshotJson(candidate.getId());
        if (snapshotJson == null || snapshotJson.isBlank()) return false;

        Game snapshotGame = CombatReplayTileRenderer.render(candidate.getInitialRenderSnapshotJson(), snapshotJson);
        if (snapshotGame == null) return false;

        Player attacker = snapshotGame.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = snapshotGame.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Tile tile = snapshotGame.getTileByPosition(candidate.getTilePosition());
        UnitHolder space = tile == null ? null : tile.getUnitHolders().get(Constants.SPACE);
        if (attacker == null
                || defender == null
                || tile == null
                || space == null
                || candidate.getWinnerFaction() == null) return false;

        LazaxCombatSupport.FleetStrength attackerRemainingStrength =
                LazaxCombatSupport.calculateFleetStrength(snapshotGame, attacker, defender, tile, space);
        LazaxCombatSupport.FleetStrength defenderRemainingStrength =
                LazaxCombatSupport.calculateFleetStrength(snapshotGame, defender, attacker, tile, space);
        int roundsObserved = SpringContext.getBean(CombatCandidateEventRepository.class)
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        candidate.setPromotionScore(CombatReplayService.computePromotionScore(
                observation,
                attackerRemainingStrength,
                defenderRemainingStrength,
                candidate.getWinnerFaction(),
                roundsObserved));
        SpringContext.getBean(CombatCandidateRepository.class).save(candidate);
        return true;
    }

    private static String extractLatestSnapshotJson(Long candidateId) {
        List<CombatCandidateEventEntity> events = SpringContext.getBean(CombatCandidateEventRepository.class)
                .findByCandidateIdOrderBySequenceNumberAsc(candidateId);
        ReplayDispatchSerializer payloadSerializer = SpringContext.getBean(ReplayDispatchSerializer.class);
        for (int i = events.size() - 1; i >= 0; i--) {
            CombatCandidateEventEntity event = events.get(i);
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
