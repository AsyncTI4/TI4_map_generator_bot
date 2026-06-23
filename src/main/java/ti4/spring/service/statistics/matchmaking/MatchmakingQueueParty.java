package ti4.spring.service.statistics.matchmaking;

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

/**
 * A matchmaking party. Every queued player belongs to one (a solo player is a party of one). A party is created
 * unqueued by "Form Group"; it becomes queued when a member clicks "Queue for Game".
 */
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

    /** Set when the party is queued; {@code null} while the party is only formed. */
    @Column(name = "queued_at")
    private Instant queuedAt;

    /**
     * The member whose {@link ti4.settings.users.UserSettings} provide the party's effective preferences. {@code null}
     * until a member queues the party, then set to that player and never changed.
     */
    @Column(name = "leader_id")
    private String leaderId;
}
