package ti4.commands2.search;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.collections4.ListUtils;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.message.BotLogger;
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
            tileEmbeds.add(Map.entry(tile, tile.getHelpMessageEmbed(includeAliases)));
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
                .map(tile -> Map.entry(tile, tile.getHelpMessageEmbed(includeAliases)))
                .forEach(tileEmbeds::add);
        }
        //TODO: upload tiles as emojis and use the URL for the image instead of as an attachment - alternatively, use the github URL link
        MessageChannel channel = event.getMessageChannel();
        CompletableFuture<ThreadChannel> futureThread = null;
        if (tileEmbeds.size() > 3) {
            if (event.getChannel() instanceof TextChannel) {
                String threadName = event.getFullCommandName() + (searchString == null ? "" : " search: " + searchString);
                futureThread = ((IThreadContainer) channel).createThreadChannel(threadName).setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).submitAfter(1, TimeUnit.SECONDS);
            }
        }

        List<MessageCreateAction> messageCreateActions = new ArrayList<>();

        for (List<Entry<TileModel, MessageEmbed>> entries : ListUtils.partition(tileEmbeds, 10)) { //max 10 embeds per message
            List<MessageEmbed> messageEmbeds = new ArrayList<>();
            List<FileUpload> fileUploads = new ArrayList<>();

            for (Entry<TileModel, MessageEmbed> entry : entries) {
                messageEmbeds.add(entry.getValue());
                try {
                    File file = new File(entry.getKey().getTilePath());
                    if (file.exists()) fileUploads.add(FileUpload.fromData(file, entry.getKey().getImagePath()));
                } catch (Exception e) {
                    BotLogger.log("Error finding image file for tile: " + entry.getKey().getImagePath(), e);
                }
            }

            if (futureThread != null && !futureThread.isDone()) channel = futureThread.join();

            messageCreateActions.add(channel.sendMessageEmbeds(messageEmbeds).addFiles(fileUploads));
        }

        for (MessageCreateAction messageCreateAction : messageCreateActions) {
            messageCreateAction.queue();
        }
    }
}
