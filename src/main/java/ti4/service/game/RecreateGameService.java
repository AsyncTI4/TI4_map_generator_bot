package ti4.service.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;

@UtilityClass
public class RecreateGameService {

    public static final String LIMBO_CATEGORY_NAME = "The in-limbo PBD Archive";
    public static final String TEST_GAME_MARKER = "::test::";
    private static final String FOW_GM_CHANNEL_SUFFIX = "-gm-room";
    private static final String FOW_MAIN_CHANNEL_SUFFIX = "-anonymous-announcements-private";

    public static RecreateGameResult recreateGame(Game game, Guild guild) {
        return recreateGame(game, guild, null);
    }

    public static RecreateGameResult recreateGame(Game game, Guild guild, @Nullable Member extraAccessMember) {
        RecreateGameResult result = new RecreateGameResult(game.getName());
        if (guild == null) {
            result.addNote("No guild was available.");
            return result;
        }

        if (game.isFowMode()) {
            recreateFogOfWarRoles(game, guild, extraAccessMember, result);
            moveFogOfWarChannelsOutOfLimbo(game, guild, result);
        } else {
            Role gameRole =
                    game.isCommunityMode() ? null : ensurePrimaryGameRole(game, guild, extraAccessMember, result);
            Category targetCategory = ensureTargetCategory(game, guild);
            TextChannel tableTalkChannel = ensurePrimaryTextChannel(
                    guild,
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
                    guild,
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
            }

            recreateCommunityRoles(game, guild, result);
            ensureCardsInfoThreads(game, result);
        }

        Helper.fixGameChannelPermissions(guild, game);
        List<String> missingPlayers = collectMissingPlayers(game, guild);
        result.getMissingPlayers().addAll(missingPlayers);

        if (!GameManager.save(game, "Recreated game resources")) {
            result.addNote("Game save failed after recreation.");
        }
        pingGame(game, result);
        return result;
    }

    public static String getSourceGameName(String gameName) {
        if (gameName == null) {
            return "";
        }
        int markerIndex = gameName.indexOf(TEST_GAME_MARKER);
        return markerIndex == -1 ? gameName : gameName.substring(0, markerIndex);
    }

    public static boolean isTestGame(String gameName) {
        return gameName != null && gameName.contains(TEST_GAME_MARKER);
    }

    public static String getSanitizedGameChannelPrefix(String gameName) {
        return sanitizeTextChannelSegment(gameName);
    }

    private static void recreateFogOfWarRoles(
            Game game, Guild guild, @Nullable Member extraAccessMember, RecreateGameResult result) {
        Role gameRole = ensureNamedRole(guild, game.getName(), true, result);
        Role gmRole = ensureNamedRole(guild, game.getName() + " GM", true, result);

        for (Player player : game.getRealPlayers()) {
            Member member = guild.getMemberById(player.getUserID());
            if (member == null) {
                continue;
            }
            guild.addRoleToMember(member, gameRole).complete();
        }

        Member owner = guild.getMemberById(game.getOwnerID());
        if (owner != null) {
            guild.addRoleToMember(owner, gmRole).complete();
        }
        if (extraAccessMember != null) {
            guild.addRoleToMember(extraAccessMember, gameRole).complete();
            guild.addRoleToMember(extraAccessMember, gmRole).complete();
        }
    }

    private static void moveFogOfWarChannelsOutOfLimbo(Game game, Guild guild, RecreateGameResult result) {
        Category targetCategory = ensureTargetCategory(game, guild);
        if (targetCategory == null) {
            return;
        }

        TextChannel gmChannel = guild.getTextChannelsByName(game.getName() + FOW_GM_CHANNEL_SUFFIX, true).stream()
                .findFirst()
                .orElse(null);
        TextChannel mainChannel = guild.getTextChannelsByName(game.getName() + FOW_MAIN_CHANNEL_SUFFIX, true).stream()
                .findFirst()
                .orElseGet(() -> {
                    TextChannel fallbackChannel = game.getMainGameChannel();
                    return fallbackChannel != null && guild.equals(fallbackChannel.getGuild()) ? fallbackChannel : null;
                });

        moveChannelIfNeeded(gmChannel, targetCategory, result);
        moveChannelIfNeeded(mainChannel, targetCategory, result);
        if (mainChannel != null) {
            game.setMainChannelID(mainChannel.getId());
        }
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

    private static void pingGame(Game game, RecreateGameResult result) {
        TextChannel channel = game.getTableTalkChannel();
        if (channel == null) {
            channel = game.getMainGameChannel();
        }
        if (channel == null) {
            return;
        }
        StringBuilder message =
                new StringBuilder(game.getPing()).append(" this game's Discord resources were recreated.");
        if (!result.getMissingPlayers().isEmpty()) {
            message.append("\nMissing from server: ").append(String.join(", ", result.getMissingPlayers()));
        }
        MessageHelper.sendMessageToChannel(channel, message.toString());
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(GMService.getGMChannel(game), message.toString());
        }
    }

    private static String getTableTalkChannelName(Game game) {
        String customName = sanitizeTextChannelSegment(game.getCustomName());
        if (customName.isBlank()) {
            return getSanitizedGameChannelPrefix(game.getName());
        }
        return getSanitizedGameChannelPrefix(game.getName()) + "-" + customName;
    }

    private static String getActionsChannelName(Game game) {
        return getSanitizedGameChannelPrefix(game.getName()) + Constants.ACTIONS_CHANNEL_SUFFIX;
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

        public RecreateGameResult(String gameName) {
            this.gameName = gameName;
        }

        public void addNote(String note) {
            notes.add(note);
        }

        public String getSummary() {
            List<String> lines = new ArrayList<>();
            lines.add("Recreated game resources for `" + gameName + "`.");
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
