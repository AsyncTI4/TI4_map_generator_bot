package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class XinComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "xin";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(
                ExtraCards.components(DraftCategory.TECH, "dsxing", DraftCategory.TECH, "dsxingsteel", "dsxingstar"),
                ExtraCards.components(DraftCategory.TECH, "dsxiny", DraftCategory.TECH, "dsxinysteel", "dsxinystar"),
                ExtraCards.components(
                        DraftCategory.MECH, "xin_mech", DraftCategory.UNIT, "xin_mechsteel", "xin_mechstar"));
    }
}
