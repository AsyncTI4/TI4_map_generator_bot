package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class AeternaComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "aeterna";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "cycle_of_reclamation",
                DraftCategory.ABILITY,
                "new_moon_phase",
                "waxing_moon_phase",
                "half_moon_phase",
                "full_moon_phase",
                "waning_moon_phase"));
    }
}
