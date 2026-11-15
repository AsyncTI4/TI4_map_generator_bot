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
@Table(name = "tournament_admin_role", uniqueConstraints = @UniqueConstraint(columnNames = "role_id"))
public class TournamentAdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "added_at")
    private Instant addedAt;

    TournamentAdminRole(String guildId, String roleId, String addedBy) {
        this.guildId = guildId;
        this.roleId = roleId;
        this.addedBy = addedBy;
        this.addedAt = Instant.now();
    }
}
