package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

public class HomeSystemDraftItem extends DraftItem {

    public HomeSystemDraftItem(String itemId) {
        super(DraftCategory.HOMESYSTEM, itemId);
    }

    public static HomeSystemDraftItem generate(String itemId) {
        return new HomeSystemDraftItem(itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        FactionModel faction = Mapper.getFaction(getItemId());
        TileModel tile = TileHelper.getTileById(faction.getHomeSystem());
        return getItemEmoji() + " " + tile.getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return Mapper.getFaction(getItemId()).getShortName() + " HS";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if ("ghost".equals(getItemId())) {
            return "Delta Wormhole, Creuss (4/2)";
        } else if ("crimson".equals(getItemId())) {
            return "Epsilon Wormhole, Ahk Creuxx (4/2)";
        }
        FactionModel faction = Mapper.getFaction(getItemId());
        TileModel tile = TileHelper.getTileById(faction.getHomeSystem());
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = tile.getPlanets();
        for (int i = 0; i < planetIds.size() - 1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.getLast()), sb);

        return sb.toString();
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        sb.append(" (");
        sb.append(planet.getResources()).append('/').append(planet.getInfluence());
        sb.append(") ");
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return FactionEmojis.getFactionIcon(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.HOMESYSTEM);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(generate(DraftCategory.HOMESYSTEM, faction.getAlias()));
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.HOMESYSTEM);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedHSs"));
        for (FactionModel faction : factions) {
            if (Arrays.asList(results).contains(faction.getAlias())) {
                continue;
            }
            allItems.add(generate(DraftCategory.HOMESYSTEM, faction.getAlias()));
        }
        return allItems;
    }
}
