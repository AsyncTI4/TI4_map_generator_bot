package ti4.spring.service.roundstats;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRoundPlayerStatsId implements Serializable {
    private String gameId;
    private String userDiscordId;
    private int round;
}
