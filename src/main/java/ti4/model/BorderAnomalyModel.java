package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.ResourceHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.enums.AutomationStatus;

@Data
public class BorderAnomalyModel implements ModelInterface {

    private static final String IMAGE_FOLDER = "borders/";

    private String id;
    private String name;
    private List<String> aliasList = new ArrayList<>();
    private String imagePath;
    private ComponentSource source;
    private Rules rules = new Rules();
    private AutomationStatus automation;
    private String automationNotes;

    @Data
    public static class Rules {
        private Adjacency adjacency = new Adjacency();
    }

    @Data
    public static class Adjacency {
        private boolean blocksIn;
        private boolean blocksOut;
    }

    @Override
    public boolean isValid() {
        return id != null && name != null && imagePath != null && source != null && automation != null;
    }

    @Override
    public String getAlias() {
        return id;
    }

    @JsonIgnore
    public String getImageFilePath() {
        return ResourceHelper.getResourceFromFolder(IMAGE_FOLDER, imagePath);
    }

    @JsonIgnore
    public boolean blocksAdjacencyIn() {
        return rules != null
                && rules.getAdjacency() != null
                && rules.getAdjacency().isBlocksIn();
    }

    @JsonIgnore
    public boolean blocksAdjacencyOut() {
        return rules != null
                && rules.getAdjacency() != null
                && rules.getAdjacency().isBlocksOut();
    }

    @JsonIgnore
    public boolean declaresAdjacencyRules() {
        return blocksAdjacencyIn() || blocksAdjacencyOut();
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        if (automation == null || automation == AutomationStatus.FULL) return name;
        return name + " (" + automation.getLabel() + ")";
    }

    @JsonIgnore
    public String getSearchString() {
        return id.replace("_", "");
    }

    public boolean matches(String input) {
        if (input == null) return false;
        String lowered = input.toLowerCase();
        return id.equalsIgnoreCase(input)
                || getSearchString().equals(lowered)
                || name.equalsIgnoreCase(input)
                || (aliasList != null && aliasList.stream().anyMatch(alias -> alias.equalsIgnoreCase(input)));
    }
}
