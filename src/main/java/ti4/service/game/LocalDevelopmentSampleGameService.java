package ti4.service.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class LocalDevelopmentSampleGameService {

    public static final String DEFAULT_SOURCE_GAME_NAME = "pbd15036";

    public static boolean isLocalDevelopmentStartup(String[] args) {
        if (args == null || args.length < 3 || args.length > 4) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (args[i] == null || args[i].isBlank()) {
                return false;
            }
        }
        // A numeric fourth argument is still treated as an additional guild/server id, not as a source game name.
        return args.length == 3 || !args[3].matches("\\d+");
    }

    public static String getStartupSourceGameName(String[] args) {
        if (!isLocalDevelopmentStartup(args) || args.length < 4 || args[3] == null || args[3].isBlank()) {
            return DEFAULT_SOURCE_GAME_NAME;
        }
        return args[3];
    }

    @Nullable
    public static RecreateGameService.RecreateGameResult createAndRecreateTestGame(
            Guild guild, @Nullable String developerUserId, @Nullable String sourceGameName) {
        if (guild == null) {
            return null;
        }
        String effectiveSourceGame =
                sourceGameName == null || sourceGameName.isBlank() ? DEFAULT_SOURCE_GAME_NAME : sourceGameName;
        String testGameName = buildTestGameName(effectiveSourceGame, UUID.randomUUID());
        Game game = cloneSourceGame(effectiveSourceGame, testGameName);
        if (game == null) {
            return null;
        }
        Member developer = developerUserId == null ? null : guild.getMemberById(developerUserId);
        return RecreateGameService.recreateGame(game, guild, developer);
    }

    public static LocalDevelopmentCleanResult cleanTestGames(@Nullable Guild guild) {
        LocalDevelopmentCleanResult result = new LocalDevelopmentCleanResult();
        List<String> testGameNames = GameManager.getGameNames().stream()
                .filter(RecreateGameService::isTestGame)
                .toList();
        for (String gameName : testGameNames) {
            Game game = GameManager.reload(gameName);
            if (game != null && guild != null) {
                deleteDiscordResources(game, guild, result);
            }
            if (GameManager.delete(gameName)) {
                result.getDeletedGames().add(gameName);
            } else {
                result.getNotes().add("Failed to delete save for " + gameName);
            }
        }
        return result;
    }

    static String buildTestGameName(String sourceGameName, UUID uuid) {
        return sourceGameName + RecreateGameService.TEST_GAME_MARKER + uuid;
    }

    @Nullable
    static Game cloneSourceGame(String sourceGameName, String targetGameName) {
        Path sourcePath = resolveSourceGamePath(sourceGameName);
        if (sourcePath == null) {
            BotLogger.warning("LocalDevelopmentSampleGameService: source game not found: " + sourceGameName);
            return null;
        }
        Game game = GameManager.loadFromPath(sourcePath);
        if (game == null) {
            BotLogger.warning("LocalDevelopmentSampleGameService: failed to load source game: " + sourcePath);
            return null;
        }
        prepareClonedGame(game, targetGameName);
        if (!GameManager.save(game, "Created local development test game from " + sourceGameName)) {
            BotLogger.warning("LocalDevelopmentSampleGameService: failed to save cloned test game: " + targetGameName);
            return null;
        }
        return GameManager.reload(targetGameName);
    }

    static void prepareClonedGame(Game game, String targetGameName) {
        game.setName(targetGameName);
        game.setHasEnded(false);
        game.setEndedDate(0);
        game.setTableTalkChannelID(null);
        game.setMainChannelID(null);
        game.setSavedChannelID(null);
        game.setSavedMessage(null);
        game.setBotMapUpdatesThreadID(null);
        game.setLaunchPostThreadID(null);
        for (Player player : game.getPlayers().values()) {
            player.setRoleIDForCommunity(null);
            player.setPrivateChannelID(null);
            player.setCardsInfoThreadID(null);
            player.setBagInfoThreadID(null);
        }
    }

    @Nullable
    static Path resolveSourceGamePath(String sourceGameName) {
        String fileName = sourceGameName.endsWith(Constants.TXT) ? sourceGameName : sourceGameName + Constants.TXT;
        Path storagePath = Storage.getGamePath(fileName);
        if (Files.exists(storagePath)) {
            return storagePath;
        }
        String resourcePath = Storage.getResourcePath();
        if (resourcePath == null) {
            return null;
        }
        Path mainResourcesPath = Path.of(resourcePath);
        Path parentPath = mainResourcesPath.getParent();
        if (parentPath == null) {
            return null;
        }
        Path testResourcePath = parentPath
                .resolveSibling("test")
                .resolve("resources")
                .resolve("maps")
                .resolve(fileName)
                .normalize();
        return Files.exists(testResourcePath) ? testResourcePath : null;
    }

    private static void deleteDiscordResources(Game game, Guild guild, LocalDevelopmentCleanResult result) {
        Set<String> deletedChannelIds = new LinkedHashSet<>();
        deleteChannel(game.getTableTalkChannel(), deletedChannelIds, result);
        deleteChannel(game.getMainGameChannel(), deletedChannelIds, result);
        for (Player player : game.getPlayers().values()) {
            deletePrivateChannel(player.getPrivateChannel(), deletedChannelIds, result);
            Role communityRole = player.getRoleForCommunity();
            if (communityRole != null && guild.equals(communityRole.getGuild())) {
                deleteRole(communityRole, result);
            }
        }

        String sanitizedPrefix = RecreateGameService.getSanitizedGameChannelPrefix(game.getName());
        for (TextChannel channel : guild.getTextChannels()) {
            if (channel.getName().startsWith(sanitizedPrefix)) {
                deleteChannel(channel, deletedChannelIds, result);
            }
        }

        for (Role role : guild.getRolesByName(game.getName(), true)) {
            deleteRole(role, result);
        }
        for (Role role : guild.getRolesByName(game.getName() + " GM", true)) {
            deleteRole(role, result);
        }

        Category category = guild.getCategoriesByName(game.getName(), true).stream()
                .findFirst()
                .orElse(null);
        if (category != null && category.getChannels().isEmpty()) {
            try {
                category.delete().complete();
                result.getDeletedCategories().add(category.getName());
            } catch (Exception e) {
                result.getNotes().add("Failed to delete category " + category.getName());
            }
        }
    }

    private static void deleteChannel(
            @Nullable TextChannel channel, Set<String> deletedChannelIds, LocalDevelopmentCleanResult result) {
        if (channel == null || !deletedChannelIds.add(channel.getId())) {
            return;
        }
        try {
            channel.delete().complete();
            result.getDeletedChannels().add(channel.getName());
        } catch (Exception e) {
            result.getNotes().add("Failed to delete channel " + channel.getName());
        }
    }

    private static void deletePrivateChannel(
            @Nullable MessageChannel channel, Set<String> deletedChannelIds, LocalDevelopmentCleanResult result) {
        if (channel instanceof TextChannel textChannel) {
            deleteChannel(textChannel, deletedChannelIds, result);
            return;
        }
        if (channel instanceof ThreadChannel threadChannel && deletedChannelIds.add(threadChannel.getId())) {
            try {
                threadChannel.delete().complete();
                result.getDeletedChannels().add(threadChannel.getName());
            } catch (Exception e) {
                result.getNotes().add("Failed to delete channel " + threadChannel.getName());
            }
        }
    }

    private static void deleteRole(Role role, LocalDevelopmentCleanResult result) {
        try {
            role.delete().complete();
            result.getDeletedRoles().add(role.getName());
        } catch (Exception e) {
            result.getNotes().add("Failed to delete role " + role.getName());
        }
    }

    @Getter
    public static class LocalDevelopmentCleanResult {
        private final List<String> deletedGames = new ArrayList<>();
        private final List<String> deletedChannels = new ArrayList<>();
        private final List<String> deletedRoles = new ArrayList<>();
        private final List<String> deletedCategories = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();

        public String getSummary() {
            List<String> lines = new ArrayList<>();
            lines.add("Deleted test games: " + deletedGames.size());
            if (!deletedGames.isEmpty()) {
                lines.add(String.join(", ", deletedGames));
            }
            lines.add("Deleted channels: " + deletedChannels.size());
            lines.add("Deleted roles: " + deletedRoles.size());
            if (!deletedCategories.isEmpty()) {
                lines.add("Deleted categories: " + String.join(", ", deletedCategories));
            }
            if (!notes.isEmpty()) {
                lines.add("Notes: " + String.join(" | ", notes));
            }
            return String.join("\n", lines);
        }
    }
}
