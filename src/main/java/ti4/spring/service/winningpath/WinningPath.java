package ti4.spring.service.winningpath;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@Entity
@Table(name = "winning_path")
class WinningPath {

    WinningPath(int playerCount, int victoryPoints, String path, int count) {
        this.playerCount = playerCount;
        this.victoryPoints = victoryPoints;
        this.path = path;
        this.count = count;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int playerCount;
    private int victoryPoints;
    private String path;
    private int count;
}