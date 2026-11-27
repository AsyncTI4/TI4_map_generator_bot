package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class BelkoseaComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "belkosea";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(
                DraftCategory.ABILITY,
                "classified_developments",
                ExtraCardText.relics(
                        "belkosea",
                        "superweaponavailyn",
                        "superweaponcaled",
                        "superweaponglatison",
                        "superweapongrom",
                        "superweaponmors")));
    }
}
