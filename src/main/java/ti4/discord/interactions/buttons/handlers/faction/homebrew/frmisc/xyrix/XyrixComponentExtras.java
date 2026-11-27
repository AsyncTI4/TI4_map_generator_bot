package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.draft.DraftCategory;
import ti4.model.Mapper;
import ti4.model.TomeCardModel;
import ti4.service.info.ExtraCardText;
import ti4.service.info.ExtraCards;
import ti4.service.info.FactionComponentExtras;

public final class XyrixComponentExtras implements FactionComponentExtras {

    @Override
    public String faction() {
        return "xyrix";
    }

    @Override
    public List<ExtraCards> extras() {
        return List.of(ExtraCards.cards(DraftCategory.ABILITY, "tomebound_tutelage", tomes()));
    }

    private static List<TextDisplay> tomes() {
        return Mapper.getTomeCards().values().stream()
                .sorted(Comparator.comparing(TomeCardModel::getName))
                .map(tome -> ExtraCardText.card(
                        "xyrix",
                        tome.getSource(),
                        tome.getName(),
                        "***First game round:*** " + tome.getFirstRoundText() + "\n***Otherwise:*** " + tome.getText(),
                        null))
                .toList();
    }
}
