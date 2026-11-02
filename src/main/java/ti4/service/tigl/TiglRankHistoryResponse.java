package ti4.service.tigl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiglRankHistoryResponse {
    private boolean success;
    private TiglRankHistoryData data;
}
