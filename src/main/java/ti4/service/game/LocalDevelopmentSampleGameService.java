package ti4.service.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class LocalDevelopmentSampleGameService {

    static final String SAMPLE_GAME_NAME = "pbd15036";
    static final String SAMPLE_GAME_FILE_NAME = SAMPLE_GAME_NAME + Constants.TXT;

    public static boolean isLocalDevelopmentStartup(String[] args) {
        return args.length == 3;
    }

    public static void seedSampleGameFileIfMissing() {
        Path sourcePath = getSampleGameSourcePath();
        if (sourcePath == null) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: RESOURCE_PATH is not configured; skipping sample game.");
            return;
        }
        if (!Files.exists(sourcePath)) {
            BotLogger.warning("LocalDevelopmentSampleGameService: sample game file not found: " + sourcePath);
            return;
        }

        try {
            copySampleGameFileIfMissing(sourcePath, Storage.getGamePath(SAMPLE_GAME_FILE_NAME));
        } catch (IOException e) {
            BotLogger.error("LocalDevelopmentSampleGameService: failed to seed local sample game file.", e);
        }
    }

    public static void bootstrapSampleGame(Guild guild, @Nullable String localUserId) {
        if (guild == null) {
            return;
        }

        Game game = GameManager.reload(SAMPLE_GAME_NAME);
        if (game == null) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: sample game could not be loaded: " + SAMPLE_GAME_NAME);
            return;
        }

        prepareGameForLocalDevelopment(game);

        Member localMember = localUserId == null ? null : guild.getMemberById(localUserId);
        Role gameRole = ensureGameRole(guild, game.getName(), localMember);
        Category category = ensureCategory(guild, game.getName());

        TextChannel tableTalkChannel = ensurePublicChannel(
                guild, category, getTableTalkChannelName(game), game.getTableTalkChannel(), gameRole, localMember);
        game.setTableTalkChannelID(tableTalkChannel.getId());

        TextChannel actionsChannel = ensurePublicChannel(
                guild, category, getActionsChannelName(game), game.getMainGameChannel(), gameRole, localMember);
        game.setMainChannelID(actionsChannel.getId());

        if (localMember != null) {
            for (Player player : game.getRealPlayers()) {
                TextChannel privateChannel = ensurePrivateChannel(
                        guild,
                        category,
                        getPrivateChannelName(game, player),
                        getPrivateTextChannel(player),
                        localMember);
                player.setPrivateChannelID(privateChannel.getId());
            }
        } else {
            BotLogger.warning("LocalDevelopmentSampleGameService: local user is not a member of guild `"
                    + guild.getName() + "`; skipping sample private channel creation.");
        }

        ThreadChannel botThread = ensureBotMapThread(game, actionsChannel);
        game.setBotMapUpdatesThreadID(botThread.getId());

        for (Player player : game.getRealPlayers()) {
            player.getCardsInfoThread();
        }

        GameManager.save(game, "Bootstrapped local development sample game");
    }

    static void copySampleGameFileIfMissing(Path sourcePath, Path targetPath) throws IOException {
        if (Files.exists(targetPath)) {
            return;
        }
        Files.createDirectories(targetPath.getParent());
        Files.copy(sourcePath, targetPath);
    }

    static void prepareGameForLocalDevelopment(Game game) {
        game.setHasEnded(false);
        game.setEndedDate(0);
    }

    @Nullable
    static Path getSampleGameSourcePath() {
        String resourcePath = Storage.getResourcePath();
        if (resourcePath == null) {
            return null;
        }
        return Path.of(resourcePath)
                .getParent()
                .resolve("../test/resources/maps/" + SAMPLE_GAME_FILE_NAME)
                .normalize();
    }

    private static Role ensureGameRole(Guild guild, String gameName, @Nullable Member localMember) {
        Role role = guild.getRolesByName(gameName, true).stream().findFirst().orElseGet(() -> guild.createRole()
                .setName(gameName)
                .setMentionable(true)
                .complete());
        if (localMember != null && !localMember.getRoles().contains(role)) {
            guild.addRoleToMember(localMember, role).complete();
        }
        return role;
    }

    private static Category ensureCategory(Guild guild, String categoryName) {
        return guild.getCategoriesByName(categoryName, true).stream()
                .findFirst()
                .orElseGet(() -> guild.createCategory(categoryName).complete());
    }

    private static TextChannel ensurePublicChannel(
            Guild guild,
            Category category,
            String channelName,
            @Nullable TextChannel existingChannel,
            Role gameRole,
            @Nullable Member localMember) {
        if (isChannelOnGuild(existingChannel, guild)) {
            return existingChannel;
        }

        TextChannel channel = guild.getTextChannelsByName(channelName, true).stream()
                .findFirst()
                .orElseGet(() -> configurePublicChannel(
                                guild.createTextChannel(channelName, category), gameRole, localMember)
                        .complete());
        if (channel.getParentCategory() == null || !category.getId().equals(channel.getParentCategoryId())) {
            channel.getManager().setParent(category).complete();
        }
        return channel;
    }

    private static ChannelAction<TextChannel> configurePublicChannel(
            ChannelAction<TextChannel> action, Role gameRole, @Nullable Member localMember) {
        long publicPermissions = Permission.VIEW_CHANNEL.getRawValue()
                | Permission.MESSAGE_SEND.getRawValue()
                | Permission.MESSAGE_HISTORY.getRawValue();
        action = action.syncPermissionOverrides().addRolePermissionOverride(gameRole.getIdLong(), publicPermissions, 0);
        if (localMember != null) {
            long memberPermissions = publicPermissions
                    | Permission.MANAGE_THREADS.getRawValue()
                    | Permission.CREATE_PRIVATE_THREADS.getRawValue()
                    | Permission.CREATE_PUBLIC_THREADS.getRawValue();
            action = action.addMemberPermissionOverride(localMember.getIdLong(), memberPermissions, 0);
        }
        return action;
    }

    private static TextChannel ensurePrivateChannel(
            Guild guild,
            Category category,
            String channelName,
            @Nullable TextChannel existingChannel,
            Member localMember) {
        if (isChannelOnGuild(existingChannel, guild)) {
            return existingChannel;
        }

        TextChannel channel = guild.getTextChannelsByName(channelName, true).stream()
                .findFirst()
                .orElseGet(() -> configurePrivateChannel(
                                guild.createTextChannel(channelName, category), guild, localMember)
                        .complete());
        if (channel.getParentCategory() == null || !category.getId().equals(channel.getParentCategoryId())) {
            channel.getManager().setParent(category).complete();
        }
        return channel;
    }

    private static ChannelAction<TextChannel> configurePrivateChannel(
            ChannelAction<TextChannel> action, Guild guild, Member localMember) {
        Role everyoneRole = guild.getPublicRole();
        long denyPermissions = Permission.VIEW_CHANNEL.getRawValue();
        long allowPermissions = Permission.VIEW_CHANNEL.getRawValue()
                | Permission.MESSAGE_SEND.getRawValue()
                | Permission.MESSAGE_HISTORY.getRawValue()
                | Permission.MANAGE_THREADS.getRawValue();
        return action.syncPermissionOverrides()
                .addRolePermissionOverride(everyoneRole.getIdLong(), 0, denyPermissions)
                .addMemberPermissionOverride(localMember.getIdLong(), allowPermissions, 0);
    }

    private static ThreadChannel ensureBotMapThread(Game game, TextChannel actionsChannel) {
        ThreadChannel existingThread = game.getBotMapUpdatesThread();
        if (existingThread != null && actionsChannel.getGuild().equals(existingThread.getGuild())) {
            return existingThread;
        }

        String threadName = game.getName() + Constants.BOT_CHANNEL_SUFFIX;
        List<ThreadChannel> matchingThreads = actionsChannel.getGuild().getThreadChannelsByName(threadName, true);
        if (!matchingThreads.isEmpty()) {
            return matchingThreads.getFirst();
        }

        return actionsChannel
                .createThreadChannel(threadName)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .complete();
    }

    private static boolean isChannelOnGuild(@Nullable TextChannel channel, Guild guild) {
        return channel != null && guild.getId().equals(channel.getGuild().getId());
    }

    @Nullable
    private static TextChannel getPrivateTextChannel(Player player) {
        return player.getPrivateChannel() instanceof TextChannel channel ? channel : null;
    }

    private static String getTableTalkChannelName(Game game) {
        String customName = sanitizeChannelName(game.getCustomName());
        if (customName.isBlank()) {
            return game.getName();
        }
        return game.getName() + "-" + customName;
    }

    private static String getActionsChannelName(Game game) {
        return game.getName() + Constants.ACTIONS_CHANNEL_SUFFIX;
    }

    private static String getPrivateChannelName(Game game, Player player) {
        return game.getName() + "-" + sanitizeChannelName(player.getUserName()) + "-private";
    }

    private static String sanitizeChannelName(String name) {
        return name.replace("/", "").replace(" ", "-").replace(".", "").replace(":", "");
    }
}
