package ti4.spring.service.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tournament_entry", uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "user_id"}))
public class TournamentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "tournament_id", nullable = false)
    private long tournamentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "waitlisted", nullable = false)
    private boolean waitlisted;

    @Column(name = "added_by_admin", nullable = false)
    private boolean addedByAdmin;

    @Column(name = "group_number")
    private Integer groupNumber;

    @Column(name = "game_name")
    private String gameName;

    TournamentEntry(long tournamentId, String userId, Instant joinedAt, boolean waitlisted, boolean addedByAdmin) {
        this.tournamentId = tournamentId;
        this.userId = userId;
        this.joinedAt = joinedAt;
        this.waitlisted = waitlisted;
        this.addedByAdmin = addedByAdmin;
    }
}
