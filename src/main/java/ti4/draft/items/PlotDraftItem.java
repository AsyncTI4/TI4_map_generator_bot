package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.GenericCardModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

public class PlotDraftItem extends DraftItem {

    public PlotDraftItem(String itemId) {
        super(DraftCategory.PLOT, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getPlotModel().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getPlotModel().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        GenericCardModel plotModel = getPlotModel();
        return plotModel.getText() + "\n";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return FactionEmojis.Firma_Obs;
    }

    @JsonIgnore
    private GenericCardModel getPlotModel() {
        return Mapper.getPlot(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems() {
        return draftable(DraftCategory.PLOT, buildAllItems());
    }

    public static List<DraftItem> buildAllItems() {
        return generateAll(
                DraftCategory.PLOT, Mapper.getPlots().values().stream().map(GenericCardModel::getAlias));
    }
}
