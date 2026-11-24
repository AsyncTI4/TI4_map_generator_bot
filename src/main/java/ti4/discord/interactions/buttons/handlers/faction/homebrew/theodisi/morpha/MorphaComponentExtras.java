package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.draft.DraftCategory;
import ti4.model.HydromorphicCardModel;
import ti4.model.Mapper;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class MorphaComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "morpha";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(DraftCategory.ABILITY, "hydromorphic", hydromorphicCards()));
    }

    private static List<TextDisplay> hydromorphicCards() {
        Map<String, HydromorphicCardModel> oneCopyPerName = new TreeMap<>();
        Mapper.getHydromorphicCards().values().stream()
                .sorted(Comparator.comparing(HydromorphicCardModel::getAlias))
                .forEach(card -> oneCopyPerName.putIfAbsent(card.getName(), card));
        return oneCopyPerName.values().stream()
                .map(card -> ExtraCardText.card(
                        "morpha", card.getSource(), card.getName(), card.getWindow() + ": " + card.getText(), null))
                .toList();
    }
}
