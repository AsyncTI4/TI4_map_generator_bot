package ti4.spring.service.statistics.matchmaking.queue;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface MatchmakingQueueSearchRepository extends JpaRepository<MatchmakingQueueSearch, Long> {

    @Transactional
    @Modifying
    @Query(value = """
                    insert into matchmaking_queue_search (
                        thread_id, message_id, player_counts, victory_point_goals, expansions,
                        paces, restrictions, tigl, tigl_ranks, created_at
                    ) values (
                        :threadId, :messageId, :playerCounts, :victoryPointGoals, :expansions,
                        :paces, :restrictions, :tigl, :tiglRanks, :createdAt
                    )
                    on conflict (thread_id) do update set
                        message_id = excluded.message_id,
                        player_counts = excluded.player_counts,
                        victory_point_goals = excluded.victory_point_goals,
                        expansions = excluded.expansions,
                        paces = excluded.paces,
                        restrictions = excluded.restrictions,
                        tigl = excluded.tigl,
                        tigl_ranks = excluded.tigl_ranks,
                        created_at = excluded.created_at
                    """, nativeQuery = true)
    void upsert(
            @Param("threadId") String threadId,
            @Param("messageId") String messageId,
            @Param("playerCounts") String playerCounts,
            @Param("victoryPointGoals") String victoryPointGoals,
            @Param("expansions") String expansions,
            @Param("paces") String paces,
            @Param("restrictions") String restrictions,
            @Param("tigl") boolean tigl,
            @Param("tiglRanks") String tiglRanks,
            @Param("createdAt") Instant createdAt);

    @Transactional
    @Modifying
    @Query("delete from MatchmakingQueueSearch search where search.threadId = :threadId")
    void deleteByThreadId(@Param("threadId") String threadId);
}
