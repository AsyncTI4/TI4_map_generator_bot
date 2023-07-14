package ti4.commands.help;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections4.ListUtils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

public class ListTiles extends HelpSubcommandData {

    public ListTiles() {
        super(Constants.LIST_TILES, "List all tiles");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALIASES, "True to also show the available aliases you can use"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getChannel().getType().isThread()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please use this command in a thread.");
            return;
        }

        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        boolean includeAliases = event.getOption(Constants.INCLUDE_ALIASES, false, OptionMapping::getAsBoolean);

        List<TileModel> tiles = TileHelper.getAllTiles().values().stream().sorted(Comparator.comparing(TileModel::getId)).toList();

        List<Entry<TileModel, MessageEmbed>> tileEmbeds = new ArrayList<>();

        for (TileModel tile : tiles) {
            MessageEmbed tileEmbed = tile.getHelpMessageEmbed(includeAliases);
            if (searchString == null || tileEmbed.getTitle().toLowerCase().contains(searchString.toLowerCase()) || (tile.getAliases() != null && tile.getAliases().toString().toLowerCase().contains(searchString.toLowerCase()))) {
                tileEmbeds.add(Map.entry(tile, tileEmbed));
            }
        }

        List<MessageCreateAction> messageCreateActions = new ArrayList<>();

        for (List<Entry<TileModel, MessageEmbed>> entries : ListUtils.partition(tileEmbeds, 10)) { //max 10 embeds per message
            List<MessageEmbed> messageEmbeds = new ArrayList<>();
            List<FileUpload> fileUploads = new ArrayList<>();

            for (Entry<TileModel, MessageEmbed> entry : entries) {
                messageEmbeds.add(entry.getValue());
                File file = new File(entry.getKey().getTilePath());
                fileUploads.add(FileUpload.fromData(file, entry.getKey().getImagePath()));
            }

            messageCreateActions.add(event.getMessageChannel().sendMessageEmbeds(messageEmbeds).addFiles(fileUploads));
        }

        for (MessageCreateAction messageCreateAction : messageCreateActions) {
            messageCreateAction.queue();
        }
    }
}
