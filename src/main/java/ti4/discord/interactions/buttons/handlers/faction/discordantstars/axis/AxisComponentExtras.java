package ti4.discord.interactions.buttons.handlers.faction.discordantstars.axis;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class AxisComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "axis";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(
                DraftCategory.ABILITY,
                "arms_dealers",
                ExtraCardText.relics("axis", "axisorderdd", "axisordercr", "axisordercv", "axisorderdn")));
    }
}
