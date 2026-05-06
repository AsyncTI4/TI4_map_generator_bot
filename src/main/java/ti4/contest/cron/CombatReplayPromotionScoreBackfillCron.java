package ti4.contest.cron;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
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

    private static final String SKIPPED_AFB_SUMMARY_TEXT = "Skipped AFB";

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
        int skippedAfbBackfilled = backfillSkippedAfbFlags(candidateRepository);
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
        BotLogger.info("Combat replay skipped-AFB backfill completed. Updated=" + skippedAfbBackfilled);
    }

    private static int backfillSkippedAfbFlags(CombatCandidateRepository candidateRepository) {
        CombatCandidateEventRepository eventRepository = SpringContext.getBean(CombatCandidateEventRepository.class);
        int updated = 0;
        for (CombatCandidateEventEntity event : eventRepository.findByEventTypeAndSummaryTextContainingIgnoreCase(
                CombatCandidateEventType.INFO, SKIPPED_AFB_SUMMARY_TEXT)) {
            if (event.getActorFaction() == null) continue;
            if (event.getCandidateId() == null) continue;
            CombatCandidateEntity candidate =
                    candidateRepository.findById(event.getCandidateId()).orElse(null);
            if (candidate == null) continue;
            if (hasSkippedAfbFlag(candidate, event.getActorFaction())) continue;
            if (!markSkippedAfb(candidate, event.getActorFaction())) continue;
            candidateRepository.save(candidate);
            updated++;
        }
        return updated;
    }

    private static boolean hasSkippedAfbFlag(CombatCandidateEntity candidate, String faction) {
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            return Boolean.TRUE.equals(candidate.getAttackerSkippedAfb());
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            return Boolean.TRUE.equals(candidate.getDefenderSkippedAfb());
        }
        return false;
    }

    private static boolean markSkippedAfb(CombatCandidateEntity candidate, String faction) {
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            candidate.setAttackerSkippedAfb(true);
            return true;
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            candidate.setDefenderSkippedAfb(true);
            return true;
        }
        return false;
    }

    private static boolean recomputePromotionScore(CombatCandidateEntity candidate) {
        CombatReplayService.InitialCombatStats initialStats = CombatReplayService.initialCombatStats(candidate);
        if (initialStats == null) return false;

        int roundsObserved = SpringContext.getBean(CombatCandidateEventRepository.class)
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        if (isDraw(candidate)) {
            candidate.setPromotionScore(CombatReplayService.computeDrawPromotionScore(initialStats, roundsObserved));
            candidate.setAttackerStrength(initialStats.attackerStrength());
            candidate.setDefenderStrength(initialStats.defenderStrength());
            candidate.setAttackerHp(initialStats.attackerHp());
            candidate.setDefenderHp(initialStats.defenderHp());
            SpringContext.getBean(CombatCandidateRepository.class).save(candidate);
            return true;
        }

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
                LazaxCombatSupport.calculateFleetStrength(attacker, defender, tile, space);
        LazaxCombatSupport.FleetStrength defenderRemainingStrength =
                LazaxCombatSupport.calculateFleetStrength(defender, attacker, tile, space);
        candidate.setPromotionScore(CombatReplayService.computePromotionScore(
                candidate,
                initialStats,
                attackerRemainingStrength,
                defenderRemainingStrength,
                candidate.getWinnerFaction(),
                roundsObserved));
        candidate.setAttackerStrength(initialStats.attackerStrength());
        candidate.setDefenderStrength(initialStats.defenderStrength());
        candidate.setAttackerHp(initialStats.attackerHp());
        candidate.setDefenderHp(initialStats.defenderHp());
        SpringContext.getBean(CombatCandidateRepository.class).save(candidate);
        return true;
    }

    private static boolean isDraw(CombatCandidateEntity candidate) {
        return candidate.getWinnerFaction() == null && candidate.getLoserFaction() == null;
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
