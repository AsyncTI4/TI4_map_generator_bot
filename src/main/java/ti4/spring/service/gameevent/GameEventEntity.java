package ti4.spring.service.gameevent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "game_event", uniqueConstraints = @UniqueConstraint(columnNames = {"gameName", "seq"}))
class GameEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gameName;
    private long seq;
    private String archetype;
    private int round;
    private String phase;
    private String faction;
    private long timestampEpochMillis;

    @Column(columnDefinition = "TEXT")
    private String payload;
}
