package ti4.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.model.TestBedScript.Shortcut;

@Data
public class TestBedShortcutGroup {
    private String group;
    private String description;
    private Boolean fog;
    private List<Shortcut> shortcuts = new ArrayList<>();
}
