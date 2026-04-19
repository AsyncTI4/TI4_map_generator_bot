package ti4.discord.interactions.commands.developer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.LocalDevelopmentSampleGameService;

class LocalDevelopment extends Subcommand {

    private static final String ACTION_CREATE = "create";
    private static final String ACTION_CLEAN = "clean";

    LocalDevelopment() {
        super(Constants.LOCAL_DEV, "Manage local development test games.");
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION, "Local development action")
                .setRequired(true)
                .addChoice("Create", ACTION_CREATE)
                .addChoice("Clean", ACTION_CLEAN));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Source game name").setRequired(false));
        addOptions(new OptionData(OptionType.ATTACHMENT, Constants.SOURCE_FILE, "Source game save file")
                .setRequired(false));
        addOptions(
                new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm cleanup").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.replyToMessage(event, "This command must be run in a server.");
            return;
        }

        String action = event.getOption(Constants.ACTION, ACTION_CREATE, OptionMapping::getAsString);
        switch (action) {
            case ACTION_CLEAN -> {
                String confirm = event.getOption(Constants.CONFIRM, "", OptionMapping::getAsString);
                if (!"YES".equalsIgnoreCase(confirm)) {
                    MessageHelper.replyToMessage(event, "Cleanup requires `confirm: YES`.");
                    return;
                }
                var result = LocalDevelopmentSampleGameService.cleanTestGames(guild);
                MessageHelper.replyToMessage(event, result.getSummary());
                return;
            }
            case ACTION_CREATE -> {
                OptionMapping sourceOption = event.getOption(Constants.SOURCE);
                OptionMapping sourceFileOption = event.getOption(Constants.SOURCE_FILE);
                if (sourceOption != null && sourceFileOption != null) {
                    MessageHelper.replyToMessage(event, "Use either `source` or `source_file`, not both.");
                    return;
                }

                String result;
                if (sourceFileOption != null) {
                    Attachment sourceFile = sourceFileOption.getAsAttachment();
                    if (!"txt".equalsIgnoreCase(sourceFile.getFileExtension())) {
                        MessageHelper.replyToMessage(event, "Source file must be a .txt file.");
                        return;
                    }
                    String contentType = sourceFile.getContentType();
                    if (contentType != null && !contentType.startsWith("text/plain")) {
                        MessageHelper.replyToMessage(event, "Source file must be plain text.");
                        return;
                    }
                    Path downloadedSourceFile = null;
                    try {
                        downloadedSourceFile = createRestrictedTempFile();
                        sourceFile
                                .getProxy()
                                .downloadToFile(downloadedSourceFile.toFile())
                                .get();
                    } catch (Exception e) {
                        MessageHelper.replyToMessage(event, "Failed to download the uploaded source file.");
                        return;
                    }
                    try {
                        result = LocalDevelopmentSampleGameService.createAndRecreateTestGameFromSourceFile(
                                guild, event.getUser().getId(), downloadedSourceFile);
                    } catch (Exception e) {
                        MessageHelper.replyToMessage(event, "Failed to process the uploaded source file.");
                        return;
                    } finally {
                        if (downloadedSourceFile != null) {
                            try {
                                Files.deleteIfExists(downloadedSourceFile);
                            } catch (Exception e) {
                                BotLogger.warning(
                                        "LocalDevelopment: failed to delete temporary source file "
                                                + downloadedSourceFile,
                                        e);
                            }
                        }
                    }
                    if (result == null) {
                        MessageHelper.replyToMessage(
                                event, "Failed to create a local development test game from the uploaded source file.");
                        return;
                    }
                } else {
                    String sourceGame = event.getOption(
                            Constants.SOURCE,
                            LocalDevelopmentSampleGameService.DEFAULT_SOURCE_GAME_NAME,
                            OptionMapping::getAsString);
                    result = LocalDevelopmentSampleGameService.createAndRecreateTestGame(
                            guild, event.getUser().getId(), sourceGame);
                    if (result == null) {
                        MessageHelper.replyToMessage(
                                event, "Failed to create a local development test game from `" + sourceGame + "`.");
                        return;
                    }
                }
                MessageHelper.replyToMessage(event, result);
                return;
            }
            default -> {
                MessageHelper.replyToMessage(event, "Unknown local development action: `" + action + "`.");
                return;
            }
        }
    }

    private static Path createRestrictedTempFile() throws Exception {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            return Files.createTempFile(
                    "ti4-local-dev-source-", Constants.TXT, PosixFilePermissions.asFileAttribute(permissions));
        } catch (UnsupportedOperationException e) {
            return Files.createTempFile("ti4-local-dev-source-", Constants.TXT);
        }
    }
}
