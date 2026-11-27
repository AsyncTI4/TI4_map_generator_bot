package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ArcanumComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "arcanum";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(
                DraftCategory.BREAKTHROUGH,
                "arcanumbtback",
                DraftCategory.TECH,
                "tharcanumpmy",
                "tharcanumpmg",
                "tharcanumpmr",
                "tharcanumpmb"));
    }
}
