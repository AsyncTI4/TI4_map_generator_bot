package ti4.spring.service.roundstats;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface GameRoundPlayerStatsSnapshotRepository
        extends JpaRepository<GameRoundPlayerStatsSnapshot, GameRoundPlayerStatsSnapshotId> {

    List<GameRoundPlayerStatsSnapshot> findByGameIdAndUndoIndex(String gameId, int undoIndex);

    @Transactional
    void deleteByGameIdAndUndoIndex(String gameId, int undoIndex);

    @Transactional
    void deleteByGameIdAndUndoIndexGreaterThan(String gameId, int undoIndex);

    @Transactional
    void deleteByGameIdAndUndoIndexNotIn(String gameId, Collection<Integer> undoIndexes);
}
