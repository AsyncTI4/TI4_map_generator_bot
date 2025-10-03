package ti4.spring.service.achievement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "player_achievement",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"user_id", "achievement_key", "game_mode"}))
public class PlayerAchievement {

    PlayerAchievement(
            String userId, String userName, String achievementKey, String achievementName, String gameMode) {
        this.userId = userId;
        this.userName = userName;
        this.achievementKey = achievementKey;
        this.achievementName = achievementName;
        this.gameMode = gameMode;
        this.count = 0;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "achievement_key", nullable = false)
    private String achievementKey;

    @Column(name = "achievement_name", nullable = false)
    private String achievementName;

    @Column(name = "game_mode", nullable = false)
    private String gameMode;

    @Column(name = "count", nullable = false)
    private int count;

    void incrementCount(String latestUserName) {
        this.userName = latestUserName;
        this.count++;
    }
}
