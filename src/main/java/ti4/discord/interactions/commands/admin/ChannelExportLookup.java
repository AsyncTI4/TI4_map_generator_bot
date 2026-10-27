package ti4.discord.interactions.commands.admin;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.storage.ChannelExportLocation;

@UtilityClass
class ChannelExportLookup {

    private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    static OptionData exportIdOption() {
        return new OptionData(
                        OptionType.STRING,
                        Constants.EXPORT_ID,
                        "Export id from the start message, e.g. 20261019-120000")
                .setRequired(true);
    }

    static OptionData channelOption() {
        return new OptionData(
                        OptionType.CHANNEL,
                        Constants.CHANNEL,
                        "Channel the export was for; only needed if the id matches more than one")
                .setRequired(false);
    }

    static Optional<ChannelExportLocation> findForEvent(SlashCommandInteractionEvent event) {
        if (Storage.getStoragePath() == null) {
            MessageHelper.sendMessageToEventChannel(event, "Storage path is not configured; exports cannot be found.");
            return Optional.empty();
        }
        String exportId = event.getOption(Constants.EXPORT_ID, "", OptionMapping::getAsString)
                .trim();
        if (!ChannelExportLocation.isValidExportId(exportId)) {
            MessageHelper.sendMessageToEventChannel(event, "`" + exportId + "` is not a valid export id.");
            return Optional.empty();
        }

        List<ChannelExportLocation> matches;
        try {
            matches = ChannelExportLocation.find(Storage.getChannelExportsDirectory(), exportId);
        } catch (IOException e) {
            BotLogger.error("Could not search channel exports for " + exportId, e);
            MessageHelper.sendMessageToEventChannel(event, "Could not read the exported_channels folder.");
            return Optional.empty();
        }

        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        if (channelOption != null) {
            String channelId = channelOption.getAsChannel().getId();
            matches = matches.stream()
                    .filter(location -> location.channelId().equals(channelId))
                    .toList();
        }

        if (matches.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No export with id `" + exportId + "` was found.");
            return Optional.empty();
        }
        if (matches.size() > 1) {
            String channels = matches.stream()
                    .map(location -> "<#" + location.channelId() + ">")
                    .collect(Collectors.joining(", "));
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Export id `" + exportId + "` matches exports of " + channels
                            + ". Pick one with the `channel` option.");
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    static long maxUploadBytes(SlashCommandInteractionEvent event) {
        return event.getGuild() != null ? event.getGuild().getMaxFileSize() : DEFAULT_MAX_UPLOAD_BYTES;
    }
}
