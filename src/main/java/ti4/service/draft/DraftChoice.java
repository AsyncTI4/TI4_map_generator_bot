package ti4.service.draft;

import lombok.Data;

@Data
public class DraftChoice {
    private final DraftableType type;
    private final String choiceKey;
    private final String buttonText;
    private final String simpleName;
    private final String inlineSummary;
    private final String buttonSuffix;
}
