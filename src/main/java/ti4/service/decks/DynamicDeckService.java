package ti4.service.decks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.ComponentType;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.game.HomebrewService.EnabledComponent;

@UtilityClass
public class DynamicDeckService {

    private static final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public static void applyToGame(Game game) {
        // Build and set dynamic decks for the game
        try {
            game.setActionCards(buildActionCardDeckShuffled(game));
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to build action_card deck", e);
        }
        try {
            game.setAgendas(buildAgendaDeckShuffled(game));
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to build agenda deck", e);
        }
        try {
            game.setExploreDeck(buildExploreDeckShuffled(game));
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to build explore deck", e);
        }
        try {
            game.setRelics(buildRelicDeckShuffled(game));
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to build relic deck", e);
        }
        // Technology deck: warm cache and apply variant swaps for consistency
        try {
            buildTechnologyDeck(game);
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to build technology deck", e);
        }
        try {
            game.swapInVariantTechs();
        } catch (Exception e) {
            BotLogger.error("DynamicDeckService: Failed to apply variant tech swaps", e);
        }
    }

    public static List<String> buildActionCardDeck(Game game) {
        return buildForType(game, "action_card");
    }

    public static List<String> buildActionCardDeckShuffled(Game game) {
        return shuffled(buildActionCardDeck(game));
    }

    public static List<String> buildAgendaDeck(Game game) {
        return buildForType(game, "agenda");
    }

    public static List<String> buildAgendaDeckShuffled(Game game) {
        return shuffled(buildAgendaDeck(game));
    }

    public static List<String> buildExploreDeck(Game game) {
        return buildForType(game, "explore");
    }

    public static List<String> buildExploreDeckShuffled(Game game) {
        return shuffled(buildExploreDeck(game));
    }

    public static List<String> buildRelicDeck(Game game) {
        return buildForType(game, "relic");
    }

    public static List<String> buildRelicDeckShuffled(Game game) {
        return shuffled(buildRelicDeck(game));
    }

    public static List<String> buildTechnologyDeck(Game game) {
        return buildForType(game, "technology");
    }

    private static List<String> buildForType(Game game, String type) {
        var ct = ti4.model.ComponentType.fromString(type);
        List<ComponentSource> sources = getSelectedSourcesForType(game, ct);
        String cacheKey = buildCacheKey(ct, sources);
        return cache.computeIfAbsent(cacheKey, k -> computeDeckForType(sources, type));
    }

    private static List<String> computeDeckForType(List<ComponentSource> sources, String type) {
        switch (type) {
            case "action_card" -> {
                return buildGeneric("action_card", sources);
            }
            case "agenda" -> {
                return buildGeneric("agenda", sources);
            }
            case "explore" -> {
                return buildGeneric("explore", sources);
            }
            case "relic" -> {
                return buildGeneric("relic", sources);
            }
            case "technology" -> {
                return buildGeneric("technology", sources);
            }
            default -> {
                return List.of();
            }
        }
    }

    private static List<String> buildGeneric(String type, List<ComponentSource> sources) {
        return switch (type) {
            case "action_card" ->
                buildIdsWithReplacements(
                        Mapper.getActionCards().values().stream()
                                .filter(m -> sources.contains(m.getSource()))
                                .toList(),
                        ActionCardModel::getAlias);
            case "agenda" ->
                buildIdsWithReplacements(
                        Mapper.getAgendas().values().stream()
                                .filter(m -> sources.contains(m.getSource()))
                                .toList(),
                        AgendaModel::getAlias);
            case "explore" ->
                buildIdsWithReplacements(
                        Mapper.getExplores().values().stream()
                                .filter(m -> sources.contains(m.getSource()))
                                .toList(),
                        ExploreModel::getAlias);
            case "relic" ->
                buildIdsWithReplacements(
                        Mapper.getRelics().values().stream()
                                .filter(m -> sources.contains(m.getSource()))
                                .toList(),
                        RelicModel::getAlias);
            case "technology" ->
                buildIdsWithReplacements(
                        Mapper.getTechs().values().stream()
                                .filter(m -> sources.contains(m.getSource()))
                                .toList(),
                        TechnologyModel::getAlias);
            default -> List.of();
        };
    }

    private static <T> Set<String> applyReplacement(
            Map<String, T> idToModel, Map<String, String> replacingIdToReplacedId) {
        Set<String> result = new HashSet<>(idToModel.keySet());
        // Remove replaced IDs if present
        for (String replacedId : replacingIdToReplacedId.values()) {
            result.remove(replacedId);
        }
        // Replacing IDs are already included via idToModel keys
        return result;
    }

    private static <T> List<String> buildIdsWithReplacements(
            List<T> models, java.util.function.Function<T, String> getAlias) {
        Map<String, T> idToModel = new HashMap<>();
        Map<String, String> replacements = new HashMap<>();
        for (T model : models) {
            String id = getAlias.apply(model);
            idToModel.put(id, model);
            try {
                var method = model.getClass().getMethod("getHomebrewReplacesID");
                Object opt = method.invoke(model);
                if (opt instanceof java.util.Optional<?> optional && optional.isPresent()) {
                    Object val = optional.get();
                    if (val != null) replacements.put(id, val.toString());
                }
            } catch (Exception ignored) {
            }
        }
        Set<String> finalIds = applyReplacement(idToModel, replacements);
        return finalIds.stream().sorted().toList();
    }

    private static String buildCacheKey(ComponentType type, List<ComponentSource> sources) {
        List<String> parts = sources.stream().map(ComponentSource::toString).sorted().toList();
        return type.toString() + "|" + String.join("+", parts);
    }

    private static List<ComponentSource> getSelectedSourcesForType(Game game, ComponentType type) {
        List<EnabledComponent> enabled = EnabledComponent.fromGame(game);
        List<ComponentSource> selected = new ArrayList<>();
        for (EnabledComponent ec : enabled) {
            if (ec.enabledTypes.contains(type)) selected.add(ec.source);
        }
        if (!selected.isEmpty()) return selected;
        // Default fallback: official sources
        return List.of(
                ComponentSource.base,
                ComponentSource.pok,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4);
    }

    private static List<String> shuffled(List<String> ids) {
        List<String> list = new ArrayList<>(ids);
        Collections.shuffle(list);
        return list;
    }

    public static void clearCache() {
        cache.clear();
    }
}
