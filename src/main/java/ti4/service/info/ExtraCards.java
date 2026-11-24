package ti4.service.info;

import java.util.Arrays;
import java.util.List;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;

public record ExtraCards(String afterCard, List<DraftItem> components, List<TextDisplay> cards) {

    public static ExtraCards components(
            DraftCategory afterCategory, String afterId, DraftCategory category, String... ids) {
        List<DraftItem> components =
                Arrays.stream(ids).map(id -> DraftItem.generate(category, id)).toList();
        return new ExtraCards(afterCategory + ":" + afterId, components, List.of());
    }

    public static ExtraCards cards(DraftCategory afterCategory, String afterId, List<TextDisplay> cards) {
        return new ExtraCards(afterCategory + ":" + afterId, List.of(), cards);
    }
}
