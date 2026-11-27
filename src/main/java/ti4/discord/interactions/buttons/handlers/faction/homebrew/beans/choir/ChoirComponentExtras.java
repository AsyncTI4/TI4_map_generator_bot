package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ChoirComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "choir";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(
                ExtraCards.components(
                        DraftCategory.TECH, "bechoiry", DraftCategory.TECH, "bechoirylament", "bechoiryrequiem"),
                ExtraCards.components(
                        DraftCategory.TECH, "bechoirr", DraftCategory.TECH, "bechoirrlament", "bechoirrrequiem"),
                ExtraCards.components(
                        DraftCategory.MECH, "choir_mech", DraftCategory.UNIT, "choir_mechlament", "choir_mechrequiem"));
    }
}
