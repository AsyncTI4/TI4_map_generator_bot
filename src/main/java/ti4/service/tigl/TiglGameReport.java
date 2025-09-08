package ti4.service.tigl;

import java.util.List;
import lombok.Data;

@Data
public class TiglGameReport {
    private String gameId;
    private int score;
    private List<TiglPlayerResult> playerResults;
    private String source;
    private long timestamp;
}
