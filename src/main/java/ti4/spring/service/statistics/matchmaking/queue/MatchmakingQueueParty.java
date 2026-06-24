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
@Table(name = "matchmaking_queue_party")
public class MatchmakingQueueParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "queued", nullable = false)
    private boolean queued;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "leader_id")
    private String leaderId;
}
