package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.helpers.PatternHelper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
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
        appendSystemTraits(tile, sb);
        List<String> planetIds = tile.getPlanets();
        for (int i = 0; i < planetIds.size() - 1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.getLast()), sb);

        return sb.toString();
    }

    private static void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        planet.getPlanetTypes().stream()
                .filter(type -> switch (type) {
                    case CULTURAL, HAZARDOUS, INDUSTRIAL -> true;
                    default -> false;
                })
                .forEach(type -> sb.append(' ').append(type.getEmoji()));
        sb.append(" (");
        sb.append(planet.getResources()).append('/').append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append('/').append(MiscEmojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            planet.getTechSpecialties().forEach(specialty -> sb.append('/').append(specialty.getEmoji()));
        }
        sb.append(") ");
        if (planet.isLegendary()) {
            sb.append('\n')
                    .append(MiscEmojis.LegendaryPlanet)
                    .append(" **")
                    .append(planet.getLegendaryAbilityName())
                    .append("**: ")
                    .append(planet.getLegendaryAbilityText());
        }
    }

    private static void appendSystemTraits(TileModel tile, StringBuilder sb) {
        List<String> traits = new ArrayList<>();
        if (tile.isAsteroidField()) traits.add(MiscEmojis.Asteroids + " Asteroid Field");
        if (tile.isGravityRift()) traits.add(MiscEmojis.GravityRift + " Gravity Rift");
        if (tile.isNebula()) traits.add(MiscEmojis.Nebula + " Nebula");
        if (tile.isSupernova()) traits.add(MiscEmojis.Supernova + " Supernova");
        if (tile.getWormholes() != null) {
            tile.getWormholes().forEach(wormhole -> traits.add(wormhole.symbol() + " Wormhole"));
        }
        if (!traits.isEmpty()) {
            sb.append(String.join(", ", traits)).append('\n');
        }
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
