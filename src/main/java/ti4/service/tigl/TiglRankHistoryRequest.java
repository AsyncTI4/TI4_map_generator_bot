package ti4.service.tigl;

import java.util.List;
import lombok.Data;

@Data
public class TiglRankHistoryRequest {
    private List<Long> discordUserIds;
}
