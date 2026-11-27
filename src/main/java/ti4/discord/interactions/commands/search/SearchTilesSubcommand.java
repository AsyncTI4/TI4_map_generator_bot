package ti4.discord.interactions.commands.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.Constants;
import ti4.image.map.TileCatalogueImage;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TileHelper;
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
    private static final String AS_IMAGE = "image";
    private static final int CATALOGUE_TIME_WARNING_SECONDS = 60;

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
                        "True: Include only tiles with legendary planets; False: exclude tiles with legendary planets"),
                new OptionData(
                        OptionType.BOOLEAN,
                        AS_IMAGE,
                        "True to draw the tiles as one picture grouped by type (includes draft tiles and hyperlanes)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, Boolean.FALSE, OptionMapping::getAsBoolean);
        int minPlanets = event.getOption(MIN_NUM_PLANET, 0, OptionMapping::getAsInt);
        int maxPlanets = event.getOption(MAX_NUM_PLANET, Integer.MAX_VALUE, OptionMapping::getAsInt);
        boolean asImage = event.getOption(AS_IMAGE, Boolean.FALSE, OptionMapping::getAsBoolean);
        boolean includeDraft = event.getOption(INCLUDE_DRAFT_TILES, asImage, OptionMapping::getAsBoolean);
        boolean includeHyperlanes = event.getOption(INCLUDE_HYPERLANES, asImage, OptionMapping::getAsBoolean);
        Boolean withAnomalies = event.getOption(WITH_ANOMALY, null, OptionMapping::getAsBoolean);
        Boolean withAsteroids = event.getOption(WITH_ASTEROID, null, OptionMapping::getAsBoolean);
        Boolean withGrifts = event.getOption(WITH_GRAVITY_RIFT, null, OptionMapping::getAsBoolean);
        Boolean withNebula = event.getOption(WITH_NEBULA, null, OptionMapping::getAsBoolean);
        Boolean withSupernova = event.getOption(WITH_SUPERNOVA, null, OptionMapping::getAsBoolean);
        Boolean withLegendaries = event.getOption(WITH_LEGENDARIES, null, OptionMapping::getAsBoolean);

        List<TileModel> tiles = new ArrayList<>();
        if (TileHelper.isValidTile(searchString)) {
            tiles.add(TileHelper.getTileById(searchString));
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
                            || withLegendaries
                                    == (tile.getPlanets() != null
                                            && tile.getPlanets().stream()
                                                    .map(Mapper::getPlanet)
                                                    .anyMatch(PlanetModel::isLegendary)))
                    .sorted(Comparator.comparing(TileModel::getId))
                    .forEach(tiles::add);
        }
        if (asImage) {
            sendCatalogueImage(event, tiles);
            return;
        }
        SearchHelper.sendSearchEmbedsToEventChannel(
                event,
                tiles.stream()
                        .map(tile -> tile.getRepresentationEmbed(includeAliases))
                        .toList());
    }

    private static void sendCatalogueImage(SlashCommandInteractionEvent event, List<TileModel> tiles) {
        List<TileModel> shown = TileCatalogueImage.catalogued(tiles);
        MessageChannel channel = event.getMessageChannel();
        if (shown.isEmpty()) {
            MessageHelper.sendMessageToChannel(channel, "No tiles match those filters.");
            return;
        }
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(channel)) {
            return;
        }
        MessageHelper.sendMessageToChannel(channel, "Drawing " + shown.size() + " tiles, grouped by type...");
        ExecutorServiceManager.runAsync(
                "tile catalogue", CATALOGUE_TIME_WARNING_SECONDS, () -> postCatalogue(channel, shown));
    }

    private static void postCatalogue(MessageChannel channel, List<TileModel> tiles) {
        try {
            TileCatalogueImage.upload(tiles)
                    .ifPresentOrElse(
                            upload -> MessageHelper.sendFileUploadToChannel(channel, upload),
                            () -> MessageHelper.sendMessageToChannel(
                                    channel,
                                    "Another tile picture is being drawn right now. Try again in a few seconds."));
        } catch (Exception e) {
            BotLogger.error("Could not draw the tile catalogue.", e);
            MessageHelper.sendMessageToChannel(channel, "Could not draw the tile picture. See the bot log.");
        }
    }
}
