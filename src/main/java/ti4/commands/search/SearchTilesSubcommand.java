package ti4.commands.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;

class SearchTilesSubcommand extends SearchComponentModelSubcommand {

    private static final String MIN_NUM_PLANET = "min_num_planets";
    private static final String MAX_NUM_PLANET = "max_num_planets";
    private static final String INCLUDE_DRAFT_TILES = "include_draft";
    private static final String INCLUDE_HYPERLANES = "include_hyperlanes";
    private static final String WITH_ANOMALY = "with_anomaly";
    private static final String WITH_ASTEROID = "with_asteroid";
    private static final String WITH_GRAVITY_RIFT = "with_gravity_rift";
    private static final String WITH_NEBULA = "with_nebula";
    private static final String WITH_SUPERNOVA = "with_supernova";
    private static final String WITH_LEGENDARIES = "with_legendary_planets";

    public SearchTilesSubcommand() {
        super(Constants.SEARCH_TILES, "List all tiles");
        addOptions(
                new OptionData(
                        OptionType.BOOLEAN,
                        Constants.INCLUDE_ALIASES,
                        "True to also show the available aliases you can use"),
                new OptionData(OptionType.INTEGER, MIN_NUM_PLANET, "Minimum number of planets on tiles"),
                new OptionData(OptionType.INTEGER, MAX_NUM_PLANET, "Maximum number of planets on tiles"),
                new OptionData(OptionType.BOOLEAN, INCLUDE_DRAFT_TILES, "Include tiles used for drafting"),
                new OptionData(OptionType.BOOLEAN, INCLUDE_HYPERLANES, "Include Hyperlane tiles"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_ANOMALY,
                        "True: Include only tiles with anomalies; False: exclude tiles with anomalies"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_ASTEROID,
                        "True: Include only tiles with asteroid fields; False: exclude tiles with asteroid fields"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_GRAVITY_RIFT,
                        "True: Include only tiles with gravity rifts; False: exclude tiles with gravity rifts"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_NEBULA,
                        "True: Include only tiles with nebulas; False: exclude tiles with nebulas"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_SUPERNOVA,
                        "True: Include only tiles with supernovas; False: exclude tiles with supernovas"),
                new OptionData(
                        OptionType.BOOLEAN,
                        WITH_LEGENDARIES,
                        "True: Include only tiles with legendary planets; False: exclude tiles with legendary planets"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        int minPlanets = event.getOption(MIN_NUM_PLANET, 0, OptionMapping::getAsInt);
        int maxPlanets = event.getOption(MAX_NUM_PLANET, Integer.MAX_VALUE, OptionMapping::getAsInt);
        boolean includeDraft = event.getOption(INCLUDE_DRAFT_TILES, false, OptionMapping::getAsBoolean);
        boolean includeHyperlanes = event.getOption(INCLUDE_HYPERLANES, false, OptionMapping::getAsBoolean);
        Boolean withAnomalies = event.getOption(WITH_ANOMALY, null, OptionMapping::getAsBoolean);
        Boolean withAsteroids = event.getOption(WITH_ASTEROID, null, OptionMapping::getAsBoolean);
        Boolean withGrifts = event.getOption(WITH_GRAVITY_RIFT, null, OptionMapping::getAsBoolean);
        Boolean withNebula = event.getOption(WITH_NEBULA, null, OptionMapping::getAsBoolean);
        Boolean withSupernova = event.getOption(WITH_SUPERNOVA, null, OptionMapping::getAsBoolean);
        Boolean withLegendaries = event.getOption(WITH_LEGENDARIES, null, OptionMapping::getAsBoolean);

        List<Entry<TileModel, MessageEmbed>> tileEmbeds = new ArrayList<>();
        if (TileHelper.isValidTile(searchString)) {
            TileModel tile = TileHelper.getTileById(searchString);
            tileEmbeds.add(Map.entry(tile, tile.getRepresentationEmbed(includeAliases)));
        } else {
            TileHelper.getAllTileModels().stream()
                    .filter(tile -> tile.search(searchString, source))
                    .filter(tile -> tile.getNumPlanets() <= maxPlanets)
                    .filter(tile -> tile.getNumPlanets() >= minPlanets)
                    .filter(tile -> includeDraft || tile.getSource() != ComponentSource.draft)
                    .filter(tile -> includeHyperlanes || !tile.isHyperlane())
                    .filter(tile -> withAnomalies == null || withAnomalies == tile.isAnomaly())
                    .filter(tile -> withAsteroids == null || withAsteroids == tile.isAsteroidField())
                    .filter(tile -> withGrifts == null || withGrifts == tile.isGravityRift())
                    .filter(tile -> withNebula == null || withNebula == tile.isNebula())
                    .filter(tile -> withSupernova == null || withSupernova == tile.isSupernova())
                    .filter(tile -> withLegendaries == null
                            || tile.getPlanets().stream()
                                    .map(Mapper::getPlanet)
                                    .anyMatch(planet -> planet.isLegendary() == withLegendaries))
                    .sorted(Comparator.comparing(TileModel::getId))
                    .map(tile -> Map.entry(tile, tile.getRepresentationEmbed(includeAliases)))
                    .forEach(tileEmbeds::add);
        }
        SearchHelper.sendSearchEmbedsToEventChannel(
                event, tileEmbeds.stream().map(Entry::getValue).toList());
    }
}
