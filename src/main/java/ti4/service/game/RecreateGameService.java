package ti4.service.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Getter;
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
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.fow.CreateFoWGameService;
import ti4.service.fow.GMService;

@UtilityClass
public class RecreateGameService {

    public static final String LIMBO_CATEGORY_NAME = "The in-limbo PBD Archive";
    public static final String TEST_GAME_MARKER = "-test-";
    private static final String LEGACY_TEST_GAME_MARKER = "::test::";
    private static final String FOW_GM_CHANNEL_SUFFIX = "-gm-room";
    private static final String FOW_ACTIONS_CHANNEL_SUFFIX = "-anonymous-announcements-private";
    private static final String FOW_GM_MAP_MESSAGE =
            "Use `/show_game_as_player` for a player-scoped Fog of War map view.";
    private static final Pattern TEST_GAME_NAME_PATTERN =
            Pattern.compile("^(?<source>.+?)" + Pattern.quote(TEST_GAME_MARKER) + "(?<suffix>\\d+)$");

    public static String recreateGame(Game game) {
        return recreateGameResult(game, game.getGuild(), null).getSummary();
    }

    public static String recreateGame(Game game, Guild guild) {
        return recreateGameResult(game, guild, null).getSummary();
    }

    /**
     * Internal helper for flows that need to adjust the detailed result before presenting it, such as appending notes
     * before converting to a summary. Command callers should use {@link #recreateGame(Game)} or
     * {@link #recreateGame(Game, Guild)} so they receive the summary string directly.
     */
    static RecreateGameResult recreateGameResult(Game game, Guild guild, @Nullable Member extraAccessMember) {
        RecreateGameResult result = new RecreateGameResult(game.getName());

        Guild targetGuild = resolveTargetGuild(game, guild);
        if (targetGuild == null) {
            result.setStatusLine("Could not recreate game resources for `" + game.getName() + "`.");
            result.addNote("No guild with capacity was available.");
            return result;
        }

        if (game.isFowMode()) {
            Role gmRole = ensureFogOfWarGmRole(game, targetGuild, extraAccessMember, result);
            Role gameRole = ensureFogOfWarPlayerRole(game, targetGuild, result);
            Category targetCategory = ensureTargetCategory(game, targetGuild);

            TextChannel gmChannel = ensurePrimaryTextChannel(
                    targetGuild,
                    game,
                    targetCategory,
                    getFogOfWarGmChannelName(game),
                    getExistingFogOfWarGmChannel(game),
                    gmRole,
                    extraAccessMember,
                    result);
            if (gmChannel != null) {
                postShowGameToFogOfWarGmChannel(game, gmChannel);
            }

            TextChannel actionsChannel = ensurePrimaryTextChannel(
                    targetGuild,
                    game,
                    targetCategory,
                    getActionsChannelName(game),
                    game.getMainGameChannel(),
                    gameRole,
                    extraAccessMember,
                    result);
            if (actionsChannel != null) {
                game.setMainChannelID(actionsChannel.getId());
                // Fog of War maps are delivered directly to player private channels instead of a shared bot thread.
                game.setBotMapUpdatesThreadID(null);
            }
            // Fog of War games do not use a public table-talk channel.
            game.setTableTalkChannelID(null);

            ensureFogOfWarPrivateChannels(game, targetGuild, targetCategory, extraAccessMember, result);
            ensureCardsInfoThreads(game, result);
            postShowGameToFogOfWarPrivateChannels(game);
            Helper.fixGameChannelPermissions(targetGuild, game);
            List<String> missingPlayers = collectMissingPlayers(game, targetGuild);
            result.getMissingPlayers().addAll(missingPlayers);
            pingGame(game, result, extraAccessMember);
            return result;
        }

        Role gameRole =
                game.isCommunityMode() ? null : ensurePrimaryGameRole(game, targetGuild, extraAccessMember, result);
        Category targetCategory = ensureTargetCategory(game, targetGuild);
        TextChannel tableTalkChannel = ensurePrimaryTextChannel(
                targetGuild,
                game,
                targetCategory,
                getTableTalkChannelName(game),
                game.getTableTalkChannel(),
                gameRole,
                extraAccessMember,
                result);
        if (tableTalkChannel != null) {
            game.setTableTalkChannelID(tableTalkChannel.getId());
        }

        TextChannel actionsChannel = ensurePrimaryTextChannel(
                targetGuild,
                game,
                targetCategory,
                getActionsChannelName(game),
                game.getMainGameChannel(),
                gameRole,
                extraAccessMember,
                result);
        if (actionsChannel != null) {
            game.setMainChannelID(actionsChannel.getId());
            ThreadChannel botThread = ensureBotMapThread(game, actionsChannel);
            if (botThread != null) {
                game.setBotMapUpdatesThreadID(botThread.getId());
            }
            postShowGameToBotMapThread(game, botThread);
        }

        recreateCommunityRoles(game, targetGuild, result);
        ensureCardsInfoThreads(game, result);

        Helper.fixGameChannelPermissions(targetGuild, game);
        List<String> missingPlayers = collectMissingPlayers(game, targetGuild);
        result.getMissingPlayers().addAll(missingPlayers);
        pingGame(game, result, extraAccessMember);
        return result;
    }

    @Nullable
    static Guild resolveTargetGuild(Game game, @Nullable Guild preferredGuild) {
        TextChannel mainChannel = game.getMainGameChannel();
        TextChannel tableTalkChannel = game.getTableTalkChannel();

        Guild limboGuild = getGuildForExistingChannels(mainChannel, tableTalkChannel, true);
        if (limboGuild != null) {
            return limboGuild;
        }

        Guild existingChannelGuild = getGuildForExistingChannels(mainChannel, tableTalkChannel, false);
        if (existingChannelGuild != null) {
            return existingChannelGuild;
        }

        if (CreateGameService.getServerCapacityForNewGames(preferredGuild) > 0) {
            return preferredGuild;
        }

        return CreateGameService.getServerWithMostCapacityForNewGame();
    }

    public static String getSourceGameName(String gameName) {
        if (gameName == null) {
            return "";
        }
        Matcher matcher = TEST_GAME_NAME_PATTERN.matcher(gameName);
        if (matcher.matches()) {
            return matcher.group("source");
        }
        int legacyMarkerIndex = gameName.indexOf(LEGACY_TEST_GAME_MARKER);
        return legacyMarkerIndex == -1 ? gameName : gameName.substring(0, legacyMarkerIndex);
    }

    public static boolean isTestGame(String gameName) {
        return gameName != null
                && (TEST_GAME_NAME_PATTERN.matcher(gameName).matches() || gameName.contains(LEGACY_TEST_GAME_MARKER));
    }

    public static String getSanitizedGameChannelPrefix(String gameName) {
        return sanitizeTextChannelSegment(gameName);
    }

    @Nullable
    private static Role ensurePrimaryGameRole(
            Game game, Guild guild, @Nullable Member extraAccessMember, RecreateGameResult result) {
        Role role = ensureNamedRole(guild, game.getName(), true, result);
        for (Player player : game.getRealPlayers()) {
            Member member = guild.getMemberById(player.getUserID());
            if (member != null) {
                guild.addRoleToMember(member, role).complete();
            }
        }
        if (extraAccessMember != null) {
            guild.addRoleToMember(extraAccessMember, role).complete();
        }
        return role;
    }

    static Role ensureFogOfWarPlayerRole(Game game, Guild guild, RecreateGameResult result) {
        Role role = ensureNamedRole(guild, game.getName(), true, result);
        for (Player player : game.getRealPlayers()) {
            if (player.isGM()) {
                continue;
            }
            Member member = guild.getMemberById(player.getUserID());
            if (member != null) {
                guild.addRoleToMember(member, role).complete();
            }
        }
        return role;
    }

    static Role ensureFogOfWarGmRole(
            Game game, Guild guild, @Nullable Member extraAccessMember, RecreateGameResult result) {
        Role gmRole = ensureNamedRole(guild, game.getName() + " GM", true, result);
        for (Player player : game.getPlayersWithGMRole()) {
            Member member = guild.getMemberById(player.getUserID());
            if (member != null) {
                guild.addRoleToMember(member, gmRole).complete();
            }
        }
        if (extraAccessMember != null) {
            guild.addRoleToMember(extraAccessMember, gmRole).complete();
        }
        return gmRole;
    }

    private static void recreateCommunityRoles(Game game, Guild guild, RecreateGameResult result) {
        if (!game.isCommunityMode()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (player.getRoleForCommunity() != null) {
                continue;
            }
            Role role = guild.createRole()
                    .setName(game.getName() + "-" + player.getUserName())
                    .setMentionable(false)
                    .complete();
            player.setRoleIDForCommunity(role.getId());
            Member member = guild.getMemberById(player.getUserID());
            if (member != null) {
                guild.addRoleToMember(member, role).complete();
            }
            result.getRecreatedRoles().add(role.getName());
        }
    }

    @Nullable
    private static Category ensureTargetCategory(Game game, Guild guild) {
        String categoryName = getTargetCategoryName(game);
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        return ensureCategoryOnGuild(guild, categoryName);
    }

    @Nullable
    private static String getTargetCategoryName(Game game) {
        if (game.isFowMode()) {
            return game.getName();
        }
        String categoryName = CreateGameService.getCategoryNameForGame(getSourceGameName(game.getName()));
        if (categoryName != null) {
            return categoryName;
        }
        TextChannel mainChannel = game.getMainGameChannel();
        if (mainChannel != null && mainChannel.getParentCategory() != null && !isInLimbo(mainChannel)) {
            return mainChannel.getParentCategory().getName();
        }
        TextChannel tableTalkChannel = game.getTableTalkChannel();
        if (tableTalkChannel != null && tableTalkChannel.getParentCategory() != null && !isInLimbo(tableTalkChannel)) {
            return tableTalkChannel.getParentCategory().getName();
        }
        return game.getName();
    }

    private static Category ensureCategoryOnGuild(Guild guild, String categoryName) {
        return guild.getCategoriesByName(categoryName, true).stream()
                .findFirst()
                .orElseGet(() -> createCategoryOnGuild(guild, categoryName));
    }

    private static Category createCategoryOnGuild(Guild guild, String categoryName) {
        EnumSet<Permission> allowViewChannel = EnumSet.of(Permission.VIEW_CHANNEL);
        EnumSet<Permission> denyViewChannel = EnumSet.of(Permission.VIEW_CHANNEL);
        Role bothelperRole = CreateGameService.getRole("Bothelper", guild);
        Role spectatorRole = CreateGameService.getRole("Spectator", guild);
        Role everyoneRole = guild.getPublicRole();
        ChannelAction<Category> createCategoryAction = guild.createCategory(categoryName);
        if (bothelperRole != null) {
            createCategoryAction =
                    createCategoryAction.addRolePermissionOverride(bothelperRole.getIdLong(), allowViewChannel, null);
        }
        if (spectatorRole != null) {
            createCategoryAction =
                    createCategoryAction.addRolePermissionOverride(spectatorRole.getIdLong(), allowViewChannel, null);
        }
        if (everyoneRole != null) {
            createCategoryAction =
                    createCategoryAction.addRolePermissionOverride(everyoneRole.getIdLong(), null, denyViewChannel);
        }
        return createCategoryAction.complete();
    }

    @Nullable
    private static TextChannel ensurePrimaryTextChannel(
            Guild guild,
            Game game,
            @Nullable Category targetCategory,
            String channelName,
            @Nullable TextChannel existingChannel,
            @Nullable Role role,
            @Nullable Member extraAccessMember,
            RecreateGameResult result) {
        TextChannel channel = existingChannel;
        if (channel == null || !guild.equals(channel.getGuild())) {
            channel = guild.getTextChannelsByName(channelName, true).stream()
                    .findFirst()
                    .orElse(null);
        }
        if (channel == null && targetCategory != null) {
            channel = createPrimaryTextChannel(guild, game, targetCategory, channelName, role, extraAccessMember);
            result.getCreatedChannels().add(channel.getName());
        }
        moveChannelIfNeeded(channel, targetCategory, result);
        if (channel != null && extraAccessMember != null) {
            ensureExtraMemberPermission(channel, extraAccessMember);
        }
        return channel;
    }

    static void ensureFogOfWarPrivateChannels(
            Game game,
            Guild guild,
            @Nullable Category targetCategory,
            @Nullable Member extraAccessMember,
            RecreateGameResult result) {
        for (Player player : game.getRealPlayers()) {
            if (player.isGM()) {
                continue;
            }
            TextChannel privateChannel = getPrivateChannel(player);
            if (privateChannel == null && targetCategory != null) {
                Member member = guild.getMemberById(player.getUserID());
                if (member == null) {
                    result.addNote("Private channel missing for " + player.getUserName());
                    continue;
                }
                CreateFoWGameService.createPrivateChannelForPlayer(member, game);
                privateChannel = getPrivateChannel(player);
                if (privateChannel != null) {
                    result.getCreatedChannels().add(privateChannel.getName());
                }
            }
            moveChannelIfNeeded(privateChannel, targetCategory, result);
            if (privateChannel != null && extraAccessMember != null) {
                ensureExtraMemberPermission(privateChannel, extraAccessMember);
            }
        }
    }

    private static TextChannel createPrimaryTextChannel(
            Guild guild,
            Game game,
            Category targetCategory,
            String channelName,
            @Nullable Role role,
            @Nullable Member extraAccessMember) {
        ChannelAction<TextChannel> action =
                guild.createTextChannel(channelName, targetCategory).syncPermissionOverrides();
        long allow = Permission.PIN_MESSAGES.getRawValue()
                | Permission.VIEW_CHANNEL.getRawValue()
                | Permission.MESSAGE_SEND.getRawValue()
                | Permission.MESSAGE_HISTORY.getRawValue();
        if (role != null) {
            action = action.addRolePermissionOverride(role.getIdLong(), allow, 0);
        } else {
            for (Player player : game.getRealPlayers()) {
                Member member = guild.getMemberById(player.getUserID());
                if (member != null) {
                    action = action.addMemberPermissionOverride(member.getIdLong(), allow, 0);
                }
            }
        }
        if (extraAccessMember != null) {
            long developerAllow = allow
                    | Permission.MANAGE_THREADS.getRawValue()
                    | Permission.CREATE_PUBLIC_THREADS.getRawValue()
                    | Permission.CREATE_PRIVATE_THREADS.getRawValue();
            action = action.addMemberPermissionOverride(extraAccessMember.getIdLong(), developerAllow, 0);
        }
        return action.complete();
    }

    @Nullable
    private static ThreadChannel ensureBotMapThread(Game game, TextChannel actionsChannel) {
        if (game.isFowMode() || actionsChannel == null) {
            return null;
        }
        ThreadChannel botThread = game.getBotMapUpdatesThread();
        if (botThread != null && actionsChannel.getGuild().equals(botThread.getGuild())) {
            return botThread;
        }
        String threadName = getSanitizedGameChannelPrefix(game.getName()) + Constants.BOT_CHANNEL_SUFFIX;
        List<ThreadChannel> matchingThreads = actionsChannel.getGuild().getThreadChannelsByName(threadName, true);
        if (!matchingThreads.isEmpty()) {
            return matchingThreads.getFirst();
        }
        return actionsChannel
                .createThreadChannel(threadName)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .complete();
    }

    private static void ensureCardsInfoThreads(Game game, RecreateGameResult result) {
        for (Player player : game.getRealPlayers()) {
            if (player.getCardsInfoThread() == null) {
                result.addNote("Cards info thread missing for " + player.getUserName());
            }
        }
    }

    private static void moveChannelIfNeeded(
            @Nullable TextChannel channel, @Nullable Category targetCategory, RecreateGameResult result) {
        if (channel == null || targetCategory == null) {
            return;
        }
        Category parentCategory = channel.getParentCategory();
        if (parentCategory == null
                || isInLimbo(channel)
                || !targetCategory.getId().equals(channel.getParentCategoryId())) {
            channel.getManager().setParent(targetCategory).complete();
            result.getMovedChannels().add(channel.getName());
        }
    }

    private static boolean isInLimbo(TextChannel channel) {
        return channel.getParentCategory() != null
                && LIMBO_CATEGORY_NAME.equalsIgnoreCase(
                        channel.getParentCategory().getName());
    }

    @Nullable
    private static Guild getGuildForExistingChannels(
            @Nullable TextChannel mainChannel, @Nullable TextChannel tableTalkChannel, boolean onlyLimboChannels) {
        if (mainChannel != null && (!onlyLimboChannels || isInLimbo(mainChannel))) {
            return mainChannel.getGuild();
        }
        if (tableTalkChannel != null && (!onlyLimboChannels || isInLimbo(tableTalkChannel))) {
            return tableTalkChannel.getGuild();
        }

        return null;
    }

    private static void ensureExtraMemberPermission(TextChannel channel, Member extraAccessMember) {
        long developerAllow = Permission.PIN_MESSAGES.getRawValue()
                | Permission.VIEW_CHANNEL.getRawValue()
                | Permission.MESSAGE_SEND.getRawValue()
                | Permission.MESSAGE_HISTORY.getRawValue()
                | Permission.MANAGE_THREADS.getRawValue()
                | Permission.CREATE_PUBLIC_THREADS.getRawValue()
                | Permission.CREATE_PRIVATE_THREADS.getRawValue();
        channel.getManager()
                .putMemberPermissionOverride(extraAccessMember.getIdLong(), developerAllow, 0)
                .complete();
    }

    private static Role ensureNamedRole(Guild guild, String roleName, boolean mentionable, RecreateGameResult result) {
        Role role = guild.getRolesByName(roleName, true).stream().findFirst().orElse(null);
        if (role != null) {
            return role;
        }
        role = guild.createRole().setName(roleName).setMentionable(mentionable).complete();
        result.getRecreatedRoles().add(role.getName());
        return role;
    }

    private static List<String> collectMissingPlayers(Game game, Guild guild) {
        List<String> missingPlayers = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (guild.getMemberById(player.getUserID()) == null) {
                missingPlayers.add(player.getUserName() + " (" + player.getUserID() + ")");
            }
        }
        return missingPlayers;
    }

    private static void pingGame(Game game, RecreateGameResult result, @Nullable Member extraAccessMember) {
        TextChannel channel = game.getTableTalkChannel();
        if (channel == null) {
            channel = game.getMainGameChannel();
        }
        if (channel == null) {
            return;
        }
        StringBuilder message = new StringBuilder(getRecreatedResourcesAnnouncement(game, result, extraAccessMember));
        MessageHelper.sendMessageToChannel(channel, message.toString());
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(GMService.getGMChannel(game), message.toString());
        }
    }

    static String getRecreatedResourcesAnnouncement(
            Game game, RecreateGameResult result, @Nullable Member extraAccessMember) {
        String pingTarget = extraAccessMember == null ? game.getPing() : extraAccessMember.getAsMention();
        StringBuilder announcement =
                new StringBuilder(pingTarget).append(" this game's Discord resources were recreated.");
        if (!result.getMissingPlayers().isEmpty()) {
            announcement.append("\nMissing from server: ").append(String.join(", ", result.getMissingPlayers()));
        }
        return announcement.toString();
    }

    static void postShowGameToBotMapThread(Game game, @Nullable ThreadChannel botThread) {
        if (botThread == null) {
            return;
        }
        ShowGameService.postShowGame(game, botThread);
    }

    static void postShowGameToFogOfWarPrivateChannels(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (player.isGM()) {
                continue;
            }
            TextChannel privateChannel = getPrivateChannel(player);
            if (privateChannel != null) {
                ShowGameService.postShowGame(game, privateChannel, player);
            }
        }
    }

    static void postShowGameToFogOfWarGmChannel(Game game, @Nullable TextChannel gmChannel) {
        if (gmChannel == null) {
            return;
        }
        MessageHelper.sendMessageToChannel(gmChannel, FOW_GM_MAP_MESSAGE);
    }

    static String getTableTalkChannelName(Game game) {
        if (isTestGame(game.getName())) {
            return getSanitizedGameChannelPrefix(game.getName());
        }
        String customName = sanitizeTextChannelSegment(game.getCustomName());
        if (customName.isBlank()) {
            return getSanitizedGameChannelPrefix(game.getName());
        }
        return getSanitizedGameChannelPrefix(game.getName()) + "-" + customName;
    }

    static String getActionsChannelName(Game game) {
        if (game.isFowMode()) {
            return game.getName() + FOW_ACTIONS_CHANNEL_SUFFIX;
        }
        return getSanitizedGameChannelPrefix(game.getName()) + Constants.ACTIONS_CHANNEL_SUFFIX;
    }

    static String getFogOfWarGmChannelName(Game game) {
        return game.getName() + FOW_GM_CHANNEL_SUFFIX;
    }

    @Nullable
    private static TextChannel getExistingFogOfWarGmChannel(Game game) {
        if (game.getGuild() == null) {
            return null;
        }
        List<TextChannel> channels = game.getGuild().getTextChannelsByName(getFogOfWarGmChannelName(game), true);
        return channels.isEmpty() ? null : channels.getFirst();
    }

    @Nullable
    private static TextChannel getPrivateChannel(Player player) {
        if (player.getPrivateChannel() instanceof TextChannel textChannel) {
            return textChannel;
        }
        return null;
    }

    private static String sanitizeTextChannelSegment(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    @Getter
    public static class RecreateGameResult {
        private final String gameName;
        private final List<String> recreatedRoles = new ArrayList<>();
        private final List<String> createdChannels = new ArrayList<>();
        private final List<String> movedChannels = new ArrayList<>();
        private final List<String> missingPlayers = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private String statusLine;

        public RecreateGameResult(String gameName) {
            this.gameName = gameName;
        }

        public void setStatusLine(String statusLine) {
            this.statusLine = statusLine;
        }

        public void addNote(String note) {
            notes.add(note);
        }

        public String getSummary() {
            List<String> lines = new ArrayList<>();
            lines.add(statusLine == null ? "Recreated game resources for `" + gameName + "`." : statusLine);
            if (!recreatedRoles.isEmpty()) {
                lines.add("Recreated roles: " + String.join(", ", recreatedRoles));
            }
            if (!createdChannels.isEmpty()) {
                lines.add("Created channels: " + String.join(", ", createdChannels));
            }
            if (!movedChannels.isEmpty()) {
                lines.add("Moved channels: " + String.join(", ", movedChannels));
            }
            if (!missingPlayers.isEmpty()) {
                lines.add("Missing from server: " + String.join(", ", missingPlayers));
            }
            if (!notes.isEmpty()) {
                lines.add("Notes: " + String.join(" | ", new LinkedHashSet<>(notes)));
            }
            return String.join("\n", lines);
        }
    }
}
