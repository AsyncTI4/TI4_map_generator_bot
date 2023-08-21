package ti4.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelicModel {
    private String id;
    private String name;
    private String effect;
    private String shortName;

    public String getName() {
        return name.replace("<:Absol:1079473959248068701>", "");
    }
}
