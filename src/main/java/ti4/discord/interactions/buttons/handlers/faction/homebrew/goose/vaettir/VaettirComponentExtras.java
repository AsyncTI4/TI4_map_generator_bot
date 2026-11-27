package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class VaettirComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "vaettir";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "old_growth",
                DraftCategory.ABILITY,
                "cultural_root_card",
                "industrial_root_card",
                "hazardous_root_card"));
    }
}
