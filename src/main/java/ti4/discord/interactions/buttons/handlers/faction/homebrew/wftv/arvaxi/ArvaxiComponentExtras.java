package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi;

import java.util.List;
import ti4.draft.DraftCategory;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class ArvaxiComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "arvaxi";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(
                DraftCategory.BREAKTHROUGH, "arvaxibt", List.of(ArvaxiBreakthroughHandler.mobilizationEngineCard())));
    }
}
