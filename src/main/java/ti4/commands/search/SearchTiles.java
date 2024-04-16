package ti4.commands.search;

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
import ti4.helpers.Helper;
import ti4.message.BotLogger;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;

public class SearchTiles extends SearchComponentModel {

    public SearchTiles() {
        super(Constants.SEARCH_TILES, "List all tiles");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);

        // if (TileHelper.isValidTile(searchString)) {
        //     event.getChannel().sendMessageEmbeds(TileHelper.getTile(searchString).getRepresentationEmbed()).queue();
        //     return;
        // }

        List<TileModel> tiles = TileHelper.getAllTiles().values().stream().filter(tile -> !TileHelper.isValidTile(searchString) || searchString.equals(tile.getId()))
            .sorted(Comparator.comparing(TileModel::getId)).toList();
        MessageChannel channel = event.getMessageChannel();

        List<Entry<TileModel, MessageEmbed>> tileEmbeds = new ArrayList<>();

        for (TileModel tile : tiles) {
            MessageEmbed tileEmbed = tile.getHelpMessageEmbed(includeAliases);
            if (Helper.embedContainsSearchTerm(tileEmbed, searchString)) tileEmbeds.add(Map.entry(tile, tileEmbed));
        }

        //TODO: upload tiles as emojis and use the URL for the image instead of as an attachment - alternatively, use the github URL link
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
