package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class IronComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "iron";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(
                DraftCategory.ABILITY,
                "salvage_collectors",
                ExtraCardText.relics(
                        "iron",
                        "salvage_war_sun",
                        "salvage_flagship",
                        "salvage_dreadnought",
                        "salvage_carrier",
                        "salvage_cruiser",
                        "salvage_destroyer")));
    }
}
