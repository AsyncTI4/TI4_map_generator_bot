package ti4.service.emoji;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.SetUtils;

import lombok.Getter;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.message.BotLogger;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;

public class ApplicationEmojiService {

    public static final String fallbackEmoji = "<a:EvensOddsRage:1080111937930678282>";
    private static final Map<String, CachedEmoji> emojis = new HashMap<>();
    private static final Map<String, EmojiFileData> emojiFiles = new HashMap<>();

    private static boolean spoofing = false;
    private static boolean cacheInitialized = false;
    private static boolean filesInitialized = false;

    private static void initAll() {
        initFromCache();
        initFileData();
    }

    public static void uploadNewEmojis() {
        initAll();
        List<EmojiFileData> newEmojis = emojiFiles.values().stream()
            .filter(data -> !emojis.containsKey(data.getName()))
            .toList();
        BotLogger.logWithTimestamp("Uploading " + newEmojis.size() + " new emojis...");
        boolean success = createAppEmojis(newEmojis);
        pushEmojiListToCache(success);
    }

    public static void reuploadStaleEmojis() {
        initAll(); // Redundant, probably. Short circuits anyway.
        List<EmojiFileData> staleEmojis = emojiFiles.values().stream()
            .filter(data -> emojis.containsKey(data.getName()))
            .filter(data -> emojis.get(data.getName()).getTimeCreated() < data.getFile().lastModified())
            .toList();
        BotLogger.logWithTimestamp("Re-uploading " + staleEmojis.size() + " stale emojis...");
        boolean success = reuploadAppEmojis(staleEmojis);
        pushEmojiListToCache(success);
    }

    public static void deleteHangingEmojis() {
        initAll(); // Redundant, probably. Short circuits anyway.
        List<CachedEmoji> hangingEmojis = emojis.values().stream()
            .filter(emoji -> !emojiFiles.containsKey(emoji.getName()))
            .toList();
        BotLogger.logWithTimestamp("Deleting " + hangingEmojis.size() + " hanging emojis...");
        boolean success = deleteAppEmojis(hangingEmojis);
        pushEmojiListToCache(success);
    }

    public static void reportMissingEnums() {
        initAll(); // Redundant, probably. Short circuits anyway.
        Set<String> emojiEnumNames = new HashSet<>(TI4Emoji.allEmojiEnums().stream().map(TI4Emoji::name).toList());
        Set<String> emojiFileNames = new HashSet<>(enumerateEmojiFilesRecursive().map(EmojiFileData::new).map(EmojiFileData::getName).toList());
        Set<String> missingEnums = SetUtils.difference(emojiFileNames, emojiEnumNames);
        if (!missingEnums.isEmpty()) {
            BotLogger.log("Missing " + missingEnums.size() + " Emoji enums: " + missingEnums);
        }
        Set<String> missingFiles = SetUtils.difference(emojiEnumNames, emojiFileNames);
        if (!missingFiles.isEmpty()) {
            BotLogger.log("Missing " + missingFiles.size() + " Emoji files: " + missingFiles);
        }
    }

    public static void spoofEmojis() {
        List<TI4Emoji> ti4Emojis = new ArrayList<>(TI4Emoji.allEmojiEnums());
        for (TI4Emoji e : ti4Emojis) {
            String formatted = "<normalEmoji>";
            if (MiscEmojis.goodDogs().contains(e)) formatted = "<goodDoggy>";
            emojis.put(e.name(), new CachedEmoji(e.name(), "1234", formatted, 0));
        }
        spoofing = true;
        cacheInitialized = true;
        initAll();
    }

    private static void initFromCache() {
        if (cacheInitialized) return;

        List<CachedEmoji> cached = ApplicationEmojiCacheService.readCachedEmojis();
        if (cached.size() == 0) {
            BotLogger.log("No cached emojis found. Initializing from Discord.");
            resetCacheFromDiscord();
        } else {
            cached.forEach(c -> emojis.put(c.getName(), c));
        }
        cacheInitialized = true;
    }

    private static void initFileData() {
        if (filesInitialized) return;
        try {
            enumerateEmojiFilesRecursive().map(EmojiFileData::new)
                .forEach(data -> emojiFiles.put(data.getName(), data));
            filesInitialized = true;
        } catch (Exception e) {
            BotLogger.log("Unknown exception initializing emojis:", e);
        }
    }

    // CREATE -------------------------------------------------------------------------------------------------------
    private static ApplicationEmoji createAppEmoji(EmojiFileData emoji) {
        try {
            return AsyncTI4DiscordBot.jda.createApplicationEmoji(emoji.getName(), emoji.getIcon()).complete();
        } catch (Exception e) {
            // Check if we failed because it already exists...
            BotLogger.log("Failed to upload emoji file: " + emoji.getName(), e);
            return null;
        }
    }

    private static boolean createAppEmojis(List<EmojiFileData> toUpload) {
        if (toUpload.isEmpty()) return true;
        Map<String, CachedEmoji> uploaded = toUpload.parallelStream()
            .map(ApplicationEmojiService::createAppEmoji)
            .filter(Objects::nonNull)
            .map(CachedEmoji::new)
            .collect(Collectors.toConcurrentMap(CachedEmoji::getName, e -> e));
        emojis.putAll(uploaded);
        return uploaded.size() == toUpload.size();
    }

    // DELETE -------------------------------------------------------------------------------------------------------
    private static boolean deleteAppEmoji(CachedEmoji emoji) {
        try {
            AsyncTI4DiscordBot.jda.retrieveApplicationEmojiById(emoji.getId()).complete().delete().complete();
            return true;
        } catch (Exception e) {
            BotLogger.log("Failed to delete emoji file: " + emoji.getName(), e);
            return false;
        }
    }

    private static boolean deleteAppEmojis(List<CachedEmoji> toDelete) {
        if (toDelete.isEmpty()) return true;
        boolean success = true;
        Map<String, Boolean> deleted = toDelete.parallelStream()
            .collect(Collectors.toConcurrentMap(CachedEmoji::getName, ApplicationEmojiService::deleteAppEmoji));
        for (Entry<String, Boolean> deleted2 : deleted.entrySet()) {
            if (deleted2.getValue()) {
                emojis.remove(deleted2.getKey());
            } else {
                success = false;
            }
        }
        return success;
    }

    // RE-UPLOAD ----------------------------------------------------------------------------------------------------
    private static ApplicationEmoji reuploadAppEmoji(EmojiFileData file) {
        String name = file.getName();
        CachedEmoji oldEmoji = emojis.get(name);
        try {
            Icon emojiIcon = file.getIcon();
            return AsyncTI4DiscordBot.jda.retrieveApplicationEmojiById(oldEmoji.getId())
                .flatMap(appEmoji -> {
                    emojis.get(name).setFormatted(fallbackEmoji);
                    return appEmoji.delete();
                })
                .flatMap(v -> AsyncTI4DiscordBot.jda.createApplicationEmoji(name, emojiIcon))
                .complete();
        } catch (Exception e) {
            BotLogger.log(Constants.jazzPing() + " Failed to upload emoji file: " + name, e);
            return null;
        }
    }

    private static boolean reuploadAppEmojis(List<EmojiFileData> toReupload) {
        if (toReupload.isEmpty()) return true;
        try {
            boolean success = true;
            for (EmojiFileData f : toReupload) {
                ApplicationEmoji emoji = reuploadAppEmoji(f);
                Thread.sleep(50);
                if (emoji == null) {
                    success = false;
                    continue;
                }
                CachedEmoji cached = new CachedEmoji(emoji);
                emojis.put(cached.getName(), cached);
            }
            return success;
        } catch (Exception e) {
            BotLogger.log(Constants.jazzPing() + " Failed to upload emoji files: ", e);
            return false;
        }
    }

    // Footgun
    private static void resetCacheFromDiscord() {
        List<ApplicationEmoji> appEmojis = AsyncTI4DiscordBot.jda.retrieveApplicationEmojis().complete();
        BotLogger.log("> - Discord has " + appEmojis.size() + " emojis.");
        emojis.clear();
        appEmojis.stream().map(CachedEmoji::new).forEach(e -> emojis.put(e.getName(), e));
        pushEmojiListToCache();
    }

    // SERVICE ------------------------------------------------------------------------------------------------------
    public static boolean isValidAppEmoji(CustomEmoji emoji) {
        CachedEmoji cached = emojis.get(emoji.getName());
        if (cached == null)
            return false;
        return cached.getFormatted().equals(emoji.getFormatted());
    }

    @Getter
    public static class EmojiFileData {
        private final File file;
        private final String name;
        private Icon icon = null;

        public Icon getIcon() throws IOException {
            if (icon == null)
                icon = Icon.from(file);
            return icon;
        }

        public EmojiFileData(File f) {
            this.file = f;
            this.name = fileName(f);
        }
    }

    public static CachedEmoji getApplicationEmoji(String name) {
        CachedEmoji fin = emojis.get(name);
        if (fin == null) {
            System.out.println("AJAHHAHHAHSFKLNFLKE - " + name);
        }
        return emojis.getOrDefault(name, null);
    }

    public static Stream<File> enumerateEmojiFilesRecursive() {
        return enumerateEmojiFilesRecursive(Storage.getAppEmojiDirectory());
    }

    private static Stream<File> enumerateEmojiFilesRecursive(File folder) {
        if (folder == null || !folder.exists()) return Stream.of();
        List<File> filesAndDirectories = Arrays.asList(folder.listFiles());
        return filesAndDirectories.stream().flatMap(fileOrDir -> {
            if (fileOrDir == null) return Stream.of();
            if (isValidEmojiFile(fileOrDir)) return Stream.of(fileOrDir);
            if (fileOrDir.isDirectory() && !isIgnoredDirectory(fileOrDir))
                return enumerateEmojiFilesRecursive(fileOrDir);
            return Stream.of();
        });
    }

    public static String fileName(File file) {
        return file.getName().replace(".png", "").replace(".jpg", "").replace(".gif", "").replace(".webp", "");
    }

    private static boolean isValidEmojiFile(File file) {
        return file.isFile() && (file.getName().endsWith(".png") || file.getName().endsWith(".jpg") || file.getName().endsWith(".gif") || file.getName().endsWith(".webp"));
    }

    private static boolean isIgnoredDirectory(File file) {
        List<String> names = List.of("New Server Pack");
        return names.contains(file.getName());
    }

    private static void pushEmojiListToCache(boolean isHealthy) {
        if (spoofing) return;
        if (!isHealthy) {
            BotLogger.log(Constants.jazzPing() + " - Uploading failed, reinitializing cache from Discord.");
            resetCacheFromDiscord();
        } else {
            pushEmojiListToCache();
        }
    }

    private static void pushEmojiListToCache() {
        if (spoofing) return;
        List<CachedEmoji> allCached = new ArrayList<>(emojis.values());
        ApplicationEmojiCacheService.saveCachedEmojis(allCached);
    }
}
