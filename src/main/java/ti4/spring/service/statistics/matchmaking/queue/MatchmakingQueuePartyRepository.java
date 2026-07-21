package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MatchmakingQueuePartyRepository extends JpaRepository<MatchmakingQueueParty, Long> {

    List<MatchmakingQueueParty> findAllByQueuedTrueOrderByQueuedAtAsc();

    @Modifying
    @Query("""
            update MatchmakingQueueParty party
            set party.leaderId = :leaderId,
                party.queued = true,
                party.tigl = :tigl,
                party.queuedAt = :queuedAt
            where party.id = :partyId
            """)
    int markQueued(
            @Param("partyId") long partyId,
            @Param("leaderId") String leaderId,
            @Param("tigl") boolean tigl,
            @Param("queuedAt") Instant queuedAt);

    @Modifying
    @Query("delete from MatchmakingQueueParty party where party.id in :partyIds")
    int deleteAllByIds(@Param("partyIds") Collection<Long> partyIds);

    @Modifying
    @Query("delete from MatchmakingQueueParty")
    int deleteAllParties();
}
