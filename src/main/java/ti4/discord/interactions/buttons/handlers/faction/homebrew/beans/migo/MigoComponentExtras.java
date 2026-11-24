package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class MigoComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "migo";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "harvest",
                DraftCategory.ABILITY,
                "harvest_blue",
                "harvest_green",
                "harvest_red",
                "harvest_yellow"));
    }
}
