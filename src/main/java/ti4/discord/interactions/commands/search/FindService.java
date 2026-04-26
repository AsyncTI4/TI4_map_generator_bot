package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.search.FindRegistry.FindItem;
import ti4.discord.interactions.commands.search.FindRegistry.FindSpec;
import ti4.helpers.Constants;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class FindService {

    private static final String ALL = "all";
    private static final Set<ComponentSource> DEFAULT_SOURCES =
            Set.of(ComponentSource.base, ComponentSource.pok, ComponentSource.thunders_edge);

    public static void execute(SlashCommandInteractionEvent event) {
        String typeKey = event.getOption(Constants.SEARCH_TYPE, null, OptionMapping::getAsString);
        if (StringUtils.isBlank(typeKey)) {
            SearchHelper.sendSearchEmbedsToEventChannel(event, List.of());
            return;
        }

        String query = event.getOption(Constants.SEARCH, "", OptionMapping::getAsString);
        boolean allTypes = isAllKeyword(typeKey);
        boolean allQuery = isAllKeyword(query);
        if (allTypes && allQuery) {
            SearchHelper.sendSearchMessageToEventChannel(
                    event, "> `type:ALL search:ALL` would return too many results.");
            return;
        }

        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        List<FindItem> items = filteredItems(typeKey, query, source);
        if (!allQuery && !allTypes) {
            FindItem exactMatch = findExactMatch(items, query);
            if (exactMatch == null) {
                exactMatch = findExactMatch(getItems(typeKey, source), query);
            }
            if (exactMatch != null) {
                SearchHelper.sendSearchEmbedsToEventChannel(
                        event, List.of(exactMatch.exactEmbed().get()));
                return;
            }
        }

        List<MessageEmbed> embeds =
                items.stream().limit(10).map(item -> item.listEmbed().get()).toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, embeds);
    }

    public static List<Command.Choice> autoCompleteType(CommandAutoCompleteInteractionEvent event) {
        String enteredValue = normalize(event.getFocusedOption().getValue());
        Stream<Command.Choice> allChoice = Stream.of(new Command.Choice("ALL", ALL));
        Stream<Command.Choice> typeChoices = FindRegistry.getTypes().stream()
                .filter(type -> contains(type.key(), enteredValue) || contains(type.displayName(), enteredValue))
                .map(type -> new Command.Choice(type.displayName(), type.key()));
        return Stream.concat(allChoice, typeChoices)
                .filter(choice -> contains(choice.getName(), enteredValue))
                .limit(25)
                .toList();
    }

    public static List<Command.Choice> autoCompleteSource(CommandAutoCompleteInteractionEvent event) {
        String enteredValue = normalize(event.getFocusedOption().getValue());
        String typeKey = event.getOption(Constants.SEARCH_TYPE, null, OptionMapping::getAsString);
        return availableSources(typeKey).stream()
                .filter(source ->
                        contains(source.toString(), enteredValue) || contains(source.prettyName(), enteredValue))
                .limit(25)
                .map(source -> new Command.Choice(source.prettyName(), source.toString()))
                .toList();
    }

    private static List<ComponentSource> availableSources(String typeKey) {
        return getAllItems(typeKey).stream()
                .map(FindItem::source)
                .distinct()
                .filter(source -> !source.isHiddenFromSearch())
                .sorted(sourceComparator())
                .toList();
    }

    private static Comparator<ComponentSource> sourceComparator() {
        return Comparator.comparingInt((ComponentSource source) -> DEFAULT_SOURCES.contains(source) ? 0 : 1)
                .thenComparing(ComponentSource::prettyName);
    }

    private static List<FindItem> filteredItems(String typeKey, String query, ComponentSource selectedSource) {
        List<FindItem> items = getItems(typeKey, selectedSource);
        if (isAllKeyword(query)) {
            return items.stream()
                    .sorted(Comparator.comparing(FindItem::autoCompleteName))
                    .toList();
        }

        String normalizedQuery = normalize(query);
        return items.stream()
                .map(item -> new RankedItem(item, score(item, normalizedQuery)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(RankedItem::score).reversed().thenComparing(item -> item.item()
                        .autoCompleteName()))
                .map(RankedItem::item)
                .toList();
    }

    private static List<FindItem> getItems(String typeKey, ComponentSource selectedSource) {
        return getAllItems(typeKey).stream()
                .filter(item -> matchesSource(item, selectedSource))
                .toList();
    }

    private static List<FindItem> getAllItems(String typeKey) {
        FindSpec type = getType(typeKey);
        if (!isAllKeyword(typeKey) && type == null) return List.of();

        Stream<FindItem> items = isAllKeyword(typeKey)
                ? FindRegistry.getTypes().stream().flatMap(spec -> spec.items().get().stream())
                : type.items().get().stream();
        return items.toList();
    }

    private static FindSpec getType(String typeKey) {
        if (StringUtils.isBlank(typeKey) || isAllKeyword(typeKey)) return null;
        return FindRegistry.getType(typeKey);
    }

    private static FindItem findExactMatch(List<FindItem> items, String query) {
        String normalizedQuery = normalize(query);
        return items.stream()
                .filter(item -> normalize(item.alias()).equals(normalizedQuery))
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesSource(FindItem item, ComponentSource selectedSource) {
        if (selectedSource != null) return item.source() == selectedSource;
        return DEFAULT_SOURCES.contains(item.source());
    }

    private static boolean isAllKeyword(String value) {
        return ALL.equals(normalize(value));
    }

    private static int score(FindItem item, String query) {
        if (StringUtils.isBlank(query)) return 0;

        int score = 0;
        score = Math.max(score, scoreText(query, item.alias(), 1000, 850));
        score = Math.max(score, scoreText(query, item.autoCompleteName(), 950, 800));
        score = Math.max(score, scoreText(query, item.nameRepresentation(), 900, 750));
        if (item.hasTextSearch()) {
            score = Math.max(score, scoreText(query, item.textSearchBlob(), 650, 500));
        }
        if (item.matches(query)) score += 100;
        return score;
    }

    private static int scoreText(String query, String text, int exactScore, int containsScore) {
        String normalizedText = normalize(text);
        if (normalizedText.equals(query)) return exactScore;
        if (normalizedText.startsWith(query)) return containsScore + 50 - normalizedText.length();
        if (normalizedText.contains(query)) return containsScore - normalizedText.indexOf(query);
        return scoreWords(query, text, containsScore - 100);
    }

    private static int scoreWords(String query, String text, int allWordsScore) {
        List<String> queryWords = words(query);
        List<String> textWords = words(text);
        if (queryWords.isEmpty() || textWords.isEmpty()) return 0;

        long matchedWords = queryWords.stream()
                .filter(queryWord -> textWords.stream().anyMatch(textWord -> textWord.contains(queryWord)))
                .count();
        if (matchedWords != queryWords.size()) return 0;

        return allWordsScore - textWords.size();
    }

    private static boolean contains(String value, String search) {
        return normalize(value).contains(search);
    }

    private static List<String> words(String value) {
        if (value == null) return List.of();
        return Stream.of(value.toLowerCase().split("[^a-z0-9]+"))
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private record RankedItem(FindItem item, int score) {}
}
