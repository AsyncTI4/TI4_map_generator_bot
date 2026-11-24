package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class BloomComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "bloom";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "strain_dispersal",
                DraftCategory.ABILITY,
                "neurotropic_dormant",
                "neurotropic_active",
                "rhizomatic_dormant",
                "rhizomatic_active",
                "carnivorous_dormant",
                "carnivorous_active"));
    }
}
