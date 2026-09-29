package ti4.service.tigl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiglPlayerRankHistory {
    private Long discordUserId;
    private TiglCurrentRanks currentRanks;
    private List<TiglRankEntry> ranks;
}
