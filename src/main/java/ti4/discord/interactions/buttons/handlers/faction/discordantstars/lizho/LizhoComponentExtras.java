package ti4.discord.interactions.buttons.handlers.faction.discordantstars.lizho;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class LizhoComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "lizho";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(
                DraftCategory.ABILITY,
                "cunning",
                ExtraCardText.traps(
                        "lizho",
                        "dshc_1_lizho",
                        "dshc_2_lizho",
                        "dshc_3_lizho",
                        "dshc_4_lizho",
                        "dshc_5_lizho",
                        "dshc_6_lizho")));
    }
}
