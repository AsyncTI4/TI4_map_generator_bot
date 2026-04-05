package ti4.spring.service.roundstats;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoundPlayerStatsSnapshotId implements Serializable {
    private String gameId;
    private int undoIndex;
    private String userDiscordId;
    private int round;
}
