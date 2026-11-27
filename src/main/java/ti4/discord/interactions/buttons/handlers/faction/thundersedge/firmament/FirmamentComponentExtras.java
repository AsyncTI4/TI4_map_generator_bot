package ti4.discord.interactions.buttons.handlers.faction.thundersedge.firmament;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class FirmamentComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "firmament";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "puppetsoftheblade",
                DraftCategory.PLOT,
                "enervate",
                "siphon",
                "seethe",
                "assail",
                "extract"));
    }
}
