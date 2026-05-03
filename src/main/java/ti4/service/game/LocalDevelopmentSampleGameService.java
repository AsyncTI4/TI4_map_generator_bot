package ti4.service.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class LocalDevelopmentSampleGameService {

    public static final String DEFAULT_SOURCE_GAME_NAME = "pbd15036";
    public static final String ENV_VAR_LOCAL_DEV_STARTUP = "LOCAL_DEV_STARTUP";
    public static final String ENV_VAR_LOCAL_DEV_SOURCE_GAME = "LOCAL_DEV_SOURCE_GAME";
    // Game save files begin with owner-id, owner-name, then game-name.
    private static final int GAME_NAME_LINE_INDEX = 2;
    private static final int MIN_GAME_FILE_LINES = GAME_NAME_LINE_INDEX + 1;
    private static final int MAX_TEST_GAME_SUFFIX = 99_999;
    private static final String TEST_SOURCES_ROOT_DIR = "test";
    private static final String TEST_SOURCES_RESOURCE_DIR = "resources";
    private static final String TEST_SOURCES_MAPS_DIR = "maps";

    public static boolean isLocalDevelopmentStartup() {
        return isLocalDevelopmentStartup(System.getenv(ENV_VAR_LOCAL_DEV_STARTUP));
    }

    static boolean isLocalDevelopmentStartup(@Nullable String localDevelopmentStartupValue) {
        return Boolean.parseBoolean(localDevelopmentStartupValue);
    }

    public static String getStartupSourceGameName() {
        return getStartupSourceGameName(System.getenv(ENV_VAR_LOCAL_DEV_SOURCE_GAME));
    }

    private static String getStartupSourceGameName(@Nullable String sourceGameName) {
        return StringUtils.defaultIfBlank(sourceGameName, DEFAULT_SOURCE_GAME_NAME);
    }

    @Nullable
    public static String createAndRecreateTestGame(
            Guild guild, @Nullable String developerUserId, @Nullable String sourceGameName) {
        if (guild == null) {
            return null;
        }
        String effectiveSourceGame =
                sourceGameName == null || sourceGameName.isBlank() ? DEFAULT_SOURCE_GAME_NAME : sourceGameName;
        String testGameName = buildTestGameName(effectiveSourceGame);
        if (testGameName == null) {
            BotLogger.warning("LocalDevelopmentSampleGameService: no available local development test game name for "
                    + effectiveSourceGame);
            return null;
        }
        Game game = cloneSourceGame(effectiveSourceGame, testGameName);
        if (game == null) {
            return null;
        }
        Member developer = developerUserId == null ? null : guild.getMemberById(developerUserId);
        RecreateGameService.RecreateGameResult result = RecreateGameService.recreateGameResult(game, guild, developer);
        GameManager.save(game, "Recreated local development test game resources");
        return result.getSummary();
    }

    @Nullable
    public static String createAndRecreateTestGameFromSourceFile(
            Guild guild, @Nullable String developerUserId, Path uploadedSourceFile) {
        if (guild == null) {
            return null;
        }
        String sourceGameName = importSourceGameFile(uploadedSourceFile);
        if (sourceGameName == null) {
            return null;
        }
        return createAndRecreateTestGame(guild, developerUserId, sourceGameName);
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

    @Nullable
    static String buildTestGameName(String sourceGameName) {
        for (int suffix = 1; suffix <= MAX_TEST_GAME_SUFFIX; suffix++) {
            String candidate = formatTestGameName(sourceGameName, suffix);
            if (isAvailableTestGameName(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static String formatTestGameName(String sourceGameName, int suffix) {
        return sourceGameName + RecreateGameService.TEST_GAME_MARKER + suffix;
    }

    private static boolean isAvailableTestGameName(String gameName) {
        return !GameManager.isValid(gameName) && !Files.exists(Storage.getGamePath(gameName + Constants.TXT));
    }

    @Nullable
    static Game cloneSourceGame(String sourceGameName, String targetGameName) {
        Path sourcePath = resolveSourceGamePath(sourceGameName);
        if (sourcePath == null) {
            BotLogger.warning("LocalDevelopmentSampleGameService: source game not found: " + sourceGameName);
            return null;
        }
        Path targetPath = Storage.getGamePath(targetGameName + Constants.TXT);
        if (!copySourceGameToStorage(sourcePath, targetPath, targetGameName)) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: failed to copy source game into storage: " + sourcePath);
            return null;
        }
        Game game = GameManager.reload(targetGameName);
        if (game == null) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: failed to reload copied test game: " + targetGameName);
            return null;
        }
        prepareClonedGame(game, targetGameName);
        GameManager.save(game, "Created local development test game from " + sourceGameName);
        return GameManager.reload(targetGameName);
    }

    static boolean copySourceGameToStorage(Path sourcePath, Path targetPath, String targetGameName) {
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            List<String> gameFileLines = Files.readAllLines(targetPath);
            if (gameFileLines.size() < MIN_GAME_FILE_LINES) {
                BotLogger.warning(
                        "LocalDevelopmentSampleGameService: copied test game file is malformed (expected at least "
                                + MIN_GAME_FILE_LINES
                                + " lines: owner-id, owner-name, and game-name): "
                                + targetPath);
                return false;
            }
            gameFileLines.set(GAME_NAME_LINE_INDEX, targetGameName);
            Files.write(targetPath, gameFileLines);
            return true;
        } catch (Exception e) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: failed to prepare copied test game file: " + targetGameName, e);
            return false;
        }
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
    static String importSourceGameFile(Path uploadedSourceFile) {
        String sourceGameName = readGameNameFromSourceFile(uploadedSourceFile);
        if (sourceGameName == null) {
            return null;
        }
        Path sourceMapsPath = getMapsSourcePath(sourceGameName + Constants.TXT);
        if (sourceMapsPath == null) {
            BotLogger.warning("LocalDevelopmentSampleGameService: source maps path unavailable for "
                    + sourceGameName
                    + " because the resource path or its parent directory is missing.");
            return null;
        }
        try {
            Files.createDirectories(sourceMapsPath.getParent());
            Files.copy(uploadedSourceFile, sourceMapsPath, StandardCopyOption.REPLACE_EXISTING);
            return sourceGameName;
        } catch (Exception e) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: failed to store uploaded source game file for "
                            + sourceGameName,
                    e);
            return null;
        }
    }

    @Nullable
    static String readGameNameFromSourceFile(Path sourcePath) {
        try {
            List<String> gameFileLines = Files.readAllLines(sourcePath);
            if (gameFileLines.size() < MIN_GAME_FILE_LINES) {
                BotLogger.warning(
                        "LocalDevelopmentSampleGameService: uploaded source game file is malformed: " + sourcePath);
                return null;
            }
            return StringUtils.trimToNull(gameFileLines.get(GAME_NAME_LINE_INDEX));
        } catch (Exception e) {
            BotLogger.warning(
                    "LocalDevelopmentSampleGameService: failed to read uploaded source game file: " + sourcePath, e);
            return null;
        }
    }

    @Nullable
    static Path resolveSourceGamePath(String sourceGameName) {
        String fileName = sourceGameName.endsWith(Constants.TXT) ? sourceGameName : sourceGameName + Constants.TXT;
        Path storagePath = Storage.getGamePath(fileName);
        if (Files.exists(storagePath)) {
            return storagePath;
        }
        Path mapsSourcePath = getMapsSourcePath(fileName);
        return mapsSourcePath != null && Files.exists(mapsSourcePath) ? mapsSourcePath : null;
    }

    @Nullable
    static Path getMapsSourcePath(String fileName) {
        String resourcePath = Storage.getResourcePath();
        if (resourcePath == null) {
            return null;
        }
        Path mainResourcesPath = Path.of(resourcePath);
        Path parentPath = mainResourcesPath.getParent();
        if (parentPath == null) {
            return null;
        }
        return parentPath
                .resolveSibling(TEST_SOURCES_ROOT_DIR)
                .resolve(TEST_SOURCES_RESOURCE_DIR)
                .resolve(TEST_SOURCES_MAPS_DIR)
                .resolve(fileName)
                .normalize();
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
