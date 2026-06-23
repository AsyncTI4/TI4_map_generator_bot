package ti4.spring.service.statistics.matchmaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A player's membership in a {@link MatchmakingQueueParty}. A user can belong to at most one party at a time. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matchmaking_queue_member", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class MatchmakingQueueMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "party_id", nullable = false)
    private long partyId;
}
