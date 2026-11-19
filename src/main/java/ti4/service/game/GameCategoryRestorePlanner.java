package ti4.service.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;

@UtilityClass
public class GameCategoryRestorePlanner {

    public static final int MAX_CHANNELS_PER_CATEGORY = 50;

    private static final Pattern GAME_CHANNEL_NAME = Pattern.compile("^pbd(\\d+)([a-z]*)(?:-.*)?$");
    private static final Pattern CATEGORY_NAME = Pattern.compile("^PBD #(\\d+)(?:-(\\d+))?(?:\\s.*)?$");

    public record CategoryRange(String name, int lower, int upper) {

        boolean contains(int gameNumber) {
            return lower <= gameNumber && gameNumber <= upper;
        }

        int width() {
            return upper - lower + 1;
        }
    }

    public record Placement<C>(String categoryName, boolean newCategory, List<C> channels) {}

    public static OptionalInt gameNumber(String channelName) {
        Matcher matcher = GAME_CHANNEL_NAME.matcher(channelName);
        if (!matcher.matches()) return OptionalInt.empty();
        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static String rematchSuffix(String channelName) {
        Matcher matcher = GAME_CHANNEL_NAME.matcher(channelName);
        return matcher.matches() ? matcher.group(2) : "";
    }

    public static Optional<CategoryRange> parseCategory(String categoryName) {
        Matcher matcher = CATEGORY_NAME.matcher(categoryName);
        if (!matcher.matches()) return Optional.empty();
        try {
            int lower = Integer.parseInt(matcher.group(1));
            int upper = matcher.group(2) == null ? lower : Integer.parseInt(matcher.group(2));
            if (upper < lower) return Optional.empty();
            return Optional.of(new CategoryRange(categoryName, lower, upper));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static int inferGamesPerCategory(List<CategoryRange> ranges, int fallback) {
        return ranges.stream()
                .filter(range -> range.width() > 1)
                .collect(Collectors.groupingBy(CategoryRange::width, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    public static <C> List<Placement<C>> plan(
            List<C> orphanChannels,
            Function<C, String> channelName,
            List<CategoryRange> existingCategories,
            int gamesPerCategory) {
        List<CategoryRange> known = new ArrayList<>(existingCategories);
        List<String> existingNames =
                existingCategories.stream().map(CategoryRange::name).toList();
        Map<String, List<C>> channelsByCategory = new LinkedHashMap<>();

        orphanChannels.stream()
                .filter(channel -> gameNumber(channelName.apply(channel)).isPresent())
                .sorted(channelOrder(channelName))
                .forEach(channel -> {
                    int gameNumber = gameNumber(channelName.apply(channel)).getAsInt();
                    CategoryRange range = known.stream()
                            .filter(candidate -> candidate.contains(gameNumber))
                            .findFirst()
                            .orElseGet(() -> {
                                CategoryRange created = newRange(gameNumber, known, gamesPerCategory);
                                known.add(created);
                                return created;
                            });
                    channelsByCategory
                            .computeIfAbsent(range.name(), name -> new ArrayList<>())
                            .add(channel);
                });

        return channelsByCategory.entrySet().stream()
                .map(entry ->
                        new Placement<>(entry.getKey(), !existingNames.contains(entry.getKey()), entry.getValue()))
                .toList();
    }

    public static <C> Comparator<C> channelOrder(Function<C, String> channelName) {
        return Comparator.<C>comparingInt(
                        channel -> gameNumber(channelName.apply(channel)).orElse(Integer.MAX_VALUE))
                .thenComparing(channel -> rematchSuffix(channelName.apply(channel)))
                .thenComparing(channel -> channelName.apply(channel).endsWith(Constants.ACTIONS_CHANNEL_SUFFIX))
                .thenComparing(channelName);
    }

    public static <K> List<K> categoryOrder(List<K> currentOrder, Function<K, String> categoryName) {
        List<K> sortedGameCategories = currentOrder.stream()
                .filter(category -> parseCategory(categoryName.apply(category)).isPresent())
                .sorted(Comparator.comparingInt(category -> parseCategory(categoryName.apply(category))
                        .orElseThrow()
                        .lower()))
                .toList();
        List<K> desired = new ArrayList<>(currentOrder.size());
        int next = 0;
        for (K category : currentOrder) {
            boolean isGameCategory = parseCategory(categoryName.apply(category)).isPresent();
            desired.add(isGameCategory ? sortedGameCategories.get(next++) : category);
        }
        return desired;
    }

    private static CategoryRange newRange(int gameNumber, List<CategoryRange> known, int gamesPerCategory) {
        int width = Math.max(1, gamesPerCategory);
        int lower = gameNumber - gameNumber % width;
        int upper = lower + width - 1;
        for (CategoryRange range : known) {
            if (range.upper() < gameNumber) lower = Math.max(lower, range.upper() + 1);
            if (range.lower() > gameNumber) upper = Math.min(upper, range.lower() - 1);
        }
        String name = lower == upper ? "PBD #" + lower : "PBD #" + lower + "-" + upper;
        return new CategoryRange(name, lower, upper);
    }
}
