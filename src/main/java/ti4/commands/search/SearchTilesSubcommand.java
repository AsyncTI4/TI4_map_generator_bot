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

    public SearchTilesSubcommand() {
        super(Constants.SEARCH_TILES, "List all tiles");
        addOptions(
            new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"),
            new OptionData(OptionType.INTEGER, MIN_NUM_PLANET, "Minimum number of planets on tiles"),
            new OptionData(OptionType.INTEGER, MAX_NUM_PLANET, "Maximum number of planets on tiles"),
            new OptionData(OptionType.BOOLEAN, INCLUDE_DRAFT_TILES, "Include tiles used for drafting"),
            new OptionData(OptionType.BOOLEAN, INCLUDE_HYPERLANES, "Include Hyperlane tiles"),
            new OptionData(OptionType.BOOLEAN, WITH_ANOMALY, "True: Include only tiles with anomalies; False: exclude tiles with anomalies"),
            new OptionData(OptionType.BOOLEAN, WITH_ASTEROID, "True: Include only tiles with asteroid fields; False: exclude tiles with asteroid fields"),
            new OptionData(OptionType.BOOLEAN, WITH_GRAVITY_RIFT, "True: Include only tiles with gravity rifts; False: exclude tiles with gravity rifts"),
            new OptionData(OptionType.BOOLEAN, WITH_NEBULA, "True: Include only tiles with nebulas; False: exclude tiles with nebulas"),
            new OptionData(OptionType.BOOLEAN, WITH_SUPERNOVA, "True: Include only tiles with supernovas; False: exclude tiles with supernovas")
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        int min_planets = event.getOption(MIN_NUM_PLANET, 0, OptionMapping::getAsInt);
        int max_planets = event.getOption(MAX_NUM_PLANET, Integer.MAX_VALUE, OptionMapping::getAsInt);
        boolean include_draft = event.getOption(INCLUDE_DRAFT_TILES, false, OptionMapping::getAsBoolean);
        boolean include_hyperlanes = event.getOption(INCLUDE_HYPERLANES, false, OptionMapping::getAsBoolean);
        Boolean with_anomalies = event.getOption(WITH_ANOMALY, null, OptionMapping::getAsBoolean);
        Boolean with_asteroids = event.getOption(WITH_ASTEROID, null, OptionMapping::getAsBoolean);
        Boolean with_grifts = event.getOption(WITH_GRAVITY_RIFT, null, OptionMapping::getAsBoolean);
        Boolean with_nebula = event.getOption(WITH_NEBULA, null, OptionMapping::getAsBoolean);
        Boolean with_supernova = event.getOption(WITH_SUPERNOVA, null, OptionMapping::getAsBoolean);

        List<Entry<TileModel, MessageEmbed>> tileEmbeds = new ArrayList<>();
        if (TileHelper.isValidTile(searchString)) {
            TileModel tile = TileHelper.getTileById(searchString);
            tileEmbeds.add(Map.entry(tile, tile.getRepresentationEmbed(includeAliases)));
        } else {
            TileHelper.getAllTileModels().stream()
                .filter(tile -> tile.search(searchString, source))
                .filter(tile -> tile.getNumPlanets() <= max_planets)
                .filter(tile -> tile.getNumPlanets() >= min_planets)
                .filter(tile -> include_draft || tile.getSource() != ComponentSource.draft)
                .filter(tile -> include_hyperlanes || !tile.isHyperlane())
                .filter(tile -> with_anomalies == null || with_anomalies == tile.isAnomaly())
                .filter(tile -> with_asteroids == null || with_asteroids == tile.isAsteroidField())
                .filter(tile -> with_grifts == null || with_grifts == tile.isGravityRift())
                .filter(tile -> with_nebula == null || with_nebula == tile.isNebula())
                .filter(tile -> with_supernova == null || with_supernova == tile.isSupernova())
                .sorted(Comparator.comparing(TileModel::getId))
                .map(tile -> Map.entry(tile, tile.getRepresentationEmbed(includeAliases)))
                .forEach(tileEmbeds::add);
        }
        SearchHelper.sendSearchEmbedsToEventChannel(event, tileEmbeds.stream().map(Entry::getValue).toList());
    }
}
