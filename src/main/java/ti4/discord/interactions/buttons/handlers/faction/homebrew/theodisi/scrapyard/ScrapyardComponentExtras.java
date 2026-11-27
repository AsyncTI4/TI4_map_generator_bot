package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.scrapyard;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ScrapyardComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "scrapyard";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "custom_rigs",
                DraftCategory.ABILITY,
                "destroyer_customrig",
                "cruiser_customrig",
                "carrier_customrig",
                "dreadnought_customrig"));
    }
}
