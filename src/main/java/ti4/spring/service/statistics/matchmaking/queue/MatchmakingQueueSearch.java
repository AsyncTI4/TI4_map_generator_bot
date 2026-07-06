package ti4.spring.service.statistics.matchmaking.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matchmaking_queue_search")
public class MatchmakingQueueSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "thread_id", nullable = false, unique = true)
    private String threadId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "player_counts")
    private String playerCounts;

    @Column(name = "victory_point_goals")
    private String victoryPointGoals;

    @Column(name = "expansions")
    private String expansions;

    @Column(name = "paces")
    private String paces;

    @Column(name = "restrictions")
    private String restrictions;

    @Column(name = "tigl", nullable = false, columnDefinition = "boolean default false")
    private boolean tigl;

    @Column(name = "tigl_ranks")
    private String tiglRanks;

    @Column(name = "created_at")
    private Instant createdAt;
}
