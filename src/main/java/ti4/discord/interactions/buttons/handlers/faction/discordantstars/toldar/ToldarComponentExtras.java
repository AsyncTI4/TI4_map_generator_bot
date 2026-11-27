package ti4.discord.interactions.buttons.handlers.faction.discordantstars.toldar;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ToldarComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "toldar";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.ABILITY,
                "the_code",
                DraftCategory.ABILITY,
                "bestow",
                "reflect",
                "ascend",
                "thwart",
                "deceive",
                "scourge"));
    }
}
