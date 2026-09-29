package ti4.service.tigl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiglRankEntry {
    private String date;
    private String league;
    private String rankName;
    private int duration;
    private String gameId;
}
