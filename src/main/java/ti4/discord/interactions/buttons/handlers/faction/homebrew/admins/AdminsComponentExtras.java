package ti4.discord.interactions.buttons.handlers.faction.homebrew.admins;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class AdminsComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "admins";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.components(DraftCategory.TECH, "adminssd", DraftCategory.TECH, "adminsy"));
    }
}
