package ti4.service.tigl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiglRankHistoryData {
    private List<TiglPlayerRankHistory> items;
}
