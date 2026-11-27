package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class DeepComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "deep";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(
                ExtraCards.components(DraftCategory.UNIT, "deep_thedeep", DraftCategory.UNIT, "deep_thedeepascended"));
    }
}
