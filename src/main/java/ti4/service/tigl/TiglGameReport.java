package ti4.service.tigl;

import java.util.List;
import lombok.Data;

@Data
public class TiglGameReport {
    private String gameId;
    private int score;
    private int round;
    private int playerCount;
    private List<TiglPlayerResult> playerResults;
    private String source;
    private long startTimestamp;
    private long endTimestamp;
    private String league;
    private List<String> events;
}
