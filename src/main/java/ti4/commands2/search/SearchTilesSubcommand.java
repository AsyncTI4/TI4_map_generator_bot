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
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.message.BotLogger;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;

class SearchTilesSubcommand extends SearchComponentModelSubcommand {

    private final String MIN_NUM_PLANET = "min_num_planets";
    private final String MAX_NUM_PLANET = "max_num_planets";

    public SearchTilesSubcommand() {
        super(Constants.SEARCH_TILES, "List all tiles");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"),
        new OptionData(OptionType.INTEGER, MIN_NUM_PLANET, "Minimum number of planets on tiles"),
        new OptionData(OptionType.INTEGER, MAX_NUM_PLANET, "Maximum number of planets on tiles"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);
        int min_planets = event.getOption(MIN_NUM_PLANET, 0, OptionMapping::getAsInt);
        int max_planets = event.getOption(MAX_NUM_PLANET, Integer.MAX_VALUE, OptionMapping::getAsInt);

        List<Entry<TileModel, MessageEmbed>> tileEmbeds = new ArrayList<>();
        if (TileHelper.isValidTile(searchString)) {
            TileModel tile = TileHelper.getTileById(searchString);
            tileEmbeds.add(Map.entry(tile, tile.getHelpMessageEmbed(includeAliases)));
        } else {
            TileHelper.getAllTileModels().stream()
                .filter(tile -> tile.search(searchString, source))
                .filter(tile -> tile.getNumPlanets() <= max_planets)
                .filter(tile -> tile.getNumPlanets() >= min_planets)
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
