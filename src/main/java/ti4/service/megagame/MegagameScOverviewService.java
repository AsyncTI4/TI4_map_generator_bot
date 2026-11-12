package ti4.service.megagame;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.game.Game;
import ti4.image.io.ImageUploads;
import ti4.image.map.MapRenderPipeline;
import ti4.image.map.MegagameGroupsImage;
import ti4.image.map.MegagameGroupsView;
import ti4.logging.BotLogger;

@UtilityClass
public class MegagameScOverviewService {

    private static final String CHANNEL_SUFFIX = "-sc-overview";
    private static final int HISTORY_TO_SEARCH = 25;

    public static String channelName(Game game) {
        return (game.getName() + CHANNEL_SUFFIX).toLowerCase();
    }

    public static String ensureChannel(Game game) {
        TextChannel existing = channelOrNull(game);
        if (existing != null) {
            post(existing, snapshot(game, "Overview refreshed"));
            return "SC overview channel: " + existing.getAsMention();
        }
        Guild guild = game.getGuild();
        if (guild == null) {
            return "Could not create the SC overview channel: no server found for this game.";
        }
        Overview overview = snapshot(game, "Overview channel created");
        TextChannel mainChannel = game.getMainGameChannel();
        Category category = mainChannel == null ? null : mainChannel.getParentCategory();
        guild.createTextChannel(channelName(game), category)
                .queue(channel -> post(channel, overview), BotLogger::catchRestError);
        return "Creating SC overview channel `#" + channelName(game) + "`.";
    }

    public static void update(Game game, String reason) {
        if (!MegagameGroupService.isActive(game)) return;
        TextChannel channel = channelOrNull(game);
        if (channel != null) {
            post(channel, snapshot(game, reason));
        }
    }

    private record Overview(MegagameGroupsView view, String content) {}

    private static Overview snapshot(Game game, String reason) {
        String content = "**Round " + game.getRound() + "**: " + reason + "\n-# Updated <t:"
                + System.currentTimeMillis() / 1000 + ":R>";
        return new Overview(MegagameGroupsView.of(game), content);
    }

    private static void post(TextChannel channel, Overview overview) {
        MegagameGroupsView view = overview.view();
        MapRenderPipeline.queueDetachedImage(
                "Megagame SC overview for " + view.gameName(),
                () -> ImageUploads.createFileUpload(
                        MegagameGroupsImage.render(view), view.gameName() + "_sc_overview", "png"),
                upload -> replaceLatestImage(channel, overview.content(), upload));
    }

    private static void replaceLatestImage(TextChannel channel, String content, FileUpload upload) {
        channel.getHistory()
                .retrievePast(HISTORY_TO_SEARCH)
                .queue(
                        messages -> latestOwnImage(channel, messages)
                                .ifPresentOrElse(
                                        message -> message.editMessage(content)
                                                .setAttachments(upload)
                                                .queue(Consumers.nop(), BotLogger::catchRestError),
                                        () -> channel.sendMessage(content)
                                                .addFiles(upload)
                                                .queue(Consumers.nop(), BotLogger::catchRestError)),
                        BotLogger::catchRestError);
    }

    private static Optional<Message> latestOwnImage(TextChannel channel, List<Message> messages) {
        long selfId = channel.getJDA().getSelfUser().getIdLong();
        return messages.stream()
                .filter(message -> message.getAuthor().getIdLong() == selfId)
                .filter(message -> !message.getAttachments().isEmpty())
                .findFirst();
    }

    private static TextChannel channelOrNull(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) return null;
        return guild.getTextChannelsByName(channelName(game), true).stream()
                .findFirst()
                .orElse(null);
    }
}
