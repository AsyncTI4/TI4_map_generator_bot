package ti4.service.tournament.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.json.JsonMapperManager;
import ti4.model.MapTemplateModel;
import ti4.model.Mapper;
import ti4.service.game.CreateGameService;
import ti4.spring.service.tournament.Tournament;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class TournamentSetupApplier {

    public static boolean hasSource(Tournament tournament) {
        return !tournament.isFogMode() && StringUtils.isNotBlank(tournament.getSetupSourceGame());
    }

    public static Game loadSource(Tournament tournament) {
        String name = tournament.getSetupSourceGame();
        if (StringUtils.isBlank(name) || !GameManager.isValid(name)) return null;
        return GameManager.getManagedGame(name).getGame();
    }

    public static Game copyableSource(Tournament tournament) {
        Game source = hasSource(tournament) ? loadSource(tournament) : null;
        return canCopy(source) ? source : null;
    }

    static boolean canCopy(Game source) {
        return source != null
                && TournamentSetupCopier.unsupportedModes(source).isEmpty()
                && startedProblem(source) == null;
    }

    public static String startedProblem(Game source) {
        if (source == null || !TournamentSetupGame.cardsDealt(source)) return null;
        return source.getName() + " has already dealt, revealed or scored cards, so its decks no longer show which"
                + " cards were removed. Use a game that hasn't started.";
    }

    public static List<String> applyToNewGame(Game source, Game target) {
        List<String> problems = TournamentSetupCopier.apply(source, target);
        postDraftMenu(source, target);
        return problems;
    }

    private static void postDraftMenu(Game source, Game target) {
        MessageChannel channel = target.getActionsChannel();
        if (channel == null) return;
        if (prefersDraftSystem(source)) {
            target.initializeDraftSystemSettings().postMessageAndButtons(channel);
        } else if (StringUtils.isNotBlank(target.getMiltyJson())) {
            MiltySettings settings = target.initializeMiltySettings();
            settings.setRandomSetup(false);
            settings.postMessageAndButtons(channel);
        } else {
            CreateGameService.offerMapSetupChoices(channel);
        }
    }

    public static List<String> summary(Game source) {
        List<String> lines = new ArrayList<>();
        lines.add("**Expansion:** "
                + (source.isTwilightsFallMode()
                        ? "Twilight's Fall"
                        : source.isThundersEdge()
                                ? "Thunder's Edge + new PoK"
                                : StringUtils.isNotBlank(source.getStoredValue("useOldPok")) ? "Old PoK" : "New PoK"));
        List<String> modes = TournamentSetupCopier.enabledModes(source).stream()
                .filter(mode -> !"Thunder's Edge".equals(mode) && !"Prophecy of Kings".equals(mode))
                .toList();
        lines.add("**Modes:** " + (modes.isEmpty() ? "none" : String.join(", ", modes)));
        lines.add("**Points:** " + source.getVp() + " VP · " + source.getMaxSOCountPerPlayer() + " secrets · "
                + source.getPublicObjectives1Peekable().size() + "/"
                + source.getPublicObjectives2Peekable().size()
                + " objectives per stage");
        lines.add("**Decks:** " + TournamentSetupCopier.describeDecks(source));
        Map<String, Set<String>> removed = TournamentSetupCopier.removedCards(source);
        int removedCount = removed.values().stream().mapToInt(Set::size).sum();
        lines.add("**Removed cards:** "
                + (removedCount == 0 ? "none" : removedCount + " across " + removed.size() + " deck(s)"));
        lines.add("**Draft:** " + draftSummary(source));
        return lines;
    }

    private static String draftSummary(Game source) {
        if (!TournamentSetupCopier.hasDraftSettings(source))
            return "no draft settings, each game sets up its own draft";
        JsonNode draft = draftNode(source);
        String slices = presetSlices(draft);
        return (prefersDraftSystem(source) ? "draft system" : "Milty") + " · map " + mapTemplate(draft) + " · "
                + (slices == null ? "random slices per game" : sliceCount(slices) + " custom slices") + " · "
                + listSize(draft, "banFactions") + " banned, " + listSize(draft, "priFactions") + " priority factions";
    }

    public static List<String> warnings(Game source, int groupSize) {
        List<String> warnings = new ArrayList<>();
        String started = startedProblem(source);
        if (started != null) warnings.add(started + " Nothing is copied.");
        List<String> unsupported = TournamentSetupCopier.unsupportedModes(source);
        if (!unsupported.isEmpty()) {
            warnings.add("Not supported yet, so nothing is copied: " + String.join(", ", unsupported) + ".");
        }
        if (!TournamentSetupCopier.hasDraftSettings(source)) return warnings;
        JsonNode draft = draftNode(source);
        String slices = presetSlices(draft);
        if (slices != null && sliceCount(slices) < groupSize) {
            warnings.add("Only " + sliceCount(slices) + " custom slices for " + groupSize + " players per game.");
        }
        MapTemplateModel template = Mapper.getMapTemplate(mapTemplate(draft));
        if (template != null && template.getPlayerCount() != null && template.getPlayerCount() != groupSize) {
            warnings.add("Map template " + template.getAlias() + " is for " + template.getPlayerCount()
                    + " players, games have " + groupSize + ".");
        }
        return warnings;
    }

    private static boolean prefersDraftSystem(Game source) {
        return TournamentSetupCopier.usesDraftSystem(source)
                && (source.isTwilightsFallMode() || TournamentSetupCopier.miltyJson(source) == null);
    }

    private static JsonNode draftNode(Game source) {
        String json = prefersDraftSystem(source)
                ? TournamentSetupCopier.draftSystemJson(source)
                : TournamentSetupCopier.miltyJson(source);
        JsonNode root = JsonMapperManager.basic().readTree(json);
        return root.has("miltySettings") ? root.get("miltySettings") : root;
    }

    private static String presetSlices(JsonNode draft) {
        String slices = findText(draft, "presetSlices");
        return StringUtils.isBlank(slices) ? null : slices;
    }

    static int sliceCount(String slices) {
        return (int) List.of(slices.replace('|', ';').split(";")).stream()
                .filter(StringUtils::isNotBlank)
                .count();
    }

    private static String mapTemplate(JsonNode draft) {
        JsonNode template = findNode(draft, "mapTemplate");
        return template == null ? "default" : template.path("chosenKey").asString("default");
    }

    private static int listSize(JsonNode draft, String field) {
        JsonNode node = findNode(draft, field);
        return node == null ? 0 : node.path("keys").size();
    }

    private static String findText(JsonNode node, String field) {
        JsonNode found = findNode(node, field);
        return found == null || !found.isString() ? null : found.asString();
    }

    private static JsonNode findNode(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        if (node.has(field)) return node.get(field);
        for (JsonNode child : node) {
            JsonNode found = findNode(child, field);
            if (found != null) return found;
        }
        return null;
    }
}
