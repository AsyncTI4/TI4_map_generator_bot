package ti4.model.metadata;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GameCreationLocks {

    public static final String JSON_DATA_FILE_NAME = "GameCreationLocks.json";

    private Map<String, Instant> usernameToLastGameCreation = new HashMap<>();
}
