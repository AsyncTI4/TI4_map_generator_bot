package ti4.service.draft;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PlayerSetupState {
    private String color;
    private String faction;
    private String homeSystemPosition;
    private boolean speaker;
}
