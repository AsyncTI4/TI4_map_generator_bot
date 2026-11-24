package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ThurvialiComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "thurviali";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "radiant_grafting",
                DraftCategory.ABILITY,
                "radiant_grafting_flight",
                "radiant_grafting_claws",
                "radiant_grafting_parturition",
                "radiant_grafting_scales",
                "radiant_grafting_phalanges"));
    }
}
