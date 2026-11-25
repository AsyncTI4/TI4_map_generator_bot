package ti4.service.fow;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class FogPresetService {

    public static Map<FOWOption, Boolean> changes(Game game, FogPreset preset) {
        Map<FOWOption, Boolean> changes = new LinkedHashMap<>();
        if (FOWPlusService.isActive(game) != preset.isFowPlus()) {
            changes.put(FOWOption.FOW_PLUS, preset.isFowPlus());
        }
        preset.optionValues().forEach((option, value) -> {
            if (FOWOptionService.isEnabled(game, option) != value) {
                changes.put(option, value);
            }
        });
        return changes;
    }

    public static void apply(Game game, FogPreset preset) {
        if (preset.isFowPlus()) {
            FOWPlusService.enable(game);
        } else if (FOWPlusService.isActive(game)) {
            FOWPlusService.disable(game);
        }
        preset.optionValues().forEach(game::setFowOption);
    }

    public static void applyAndAnnounce(Game game, FogPreset preset) {
        boolean ghostHexesWereOn = game.getFowOption(FOWOption.GHOST_HEXES);
        boolean fowPlusWasOn = FOWPlusService.isActive(game);
        apply(game, preset);

        MessageChannel gmChannel = GMService.getGMChannel(game);
        if (gmChannel != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    gmChannel, appliedText(game, preset, fowPlusWasOn), FogGameSummaryService.fogOptionsEmbed(game));
        }
        MessageChannel mainChannel = game.getMainGameChannel();
        if (!ghostHexesWereOn && game.getFowOption(FOWOption.GHOST_HEXES) && mainChannel != null) {
            MessageHelper.sendMessageToChannel(mainChannel, FogStandardService.ghostHexNote());
        }
    }

    public static Optional<FogPreset> matching(Game game) {
        return Stream.of(FogPreset.values())
                .filter(preset -> changes(game, preset).isEmpty())
                .findFirst();
    }

    public static String status(Game game) {
        Optional<FogPreset> matching = matching(game);
        if (matching.isPresent()) {
            return "Preset: **" + matching.get().getTitle() + "**";
        }
        FogPreset closest = Stream.of(FogPreset.values())
                .min(Comparator.comparingInt(preset -> changes(game, preset).size()))
                .orElseThrow();
        int differences = changes(game, closest).size();
        return "Preset: custom (closest is **" + closest.getTitle() + "**, " + differences
                + (differences == 1 ? " difference)" : " differences)");
    }

    public static String preview(Game game, FogPreset preset) {
        StringBuilder sb = new StringBuilder("### Apply the ")
                .append(preset.getTitle())
                .append(" preset?\n-# ")
                .append(preset.getDescription())
                .append("\n\n");
        Map<FOWOption, Boolean> changes = changes(game, preset);
        if (changes.isEmpty()) {
            return sb.append("Nothing changes: the game already matches this preset.")
                    .toString();
        }
        appendTitles(sb, "✅ Turns on: ", changes, true);
        appendTitles(sb, "🚫 Turns off: ", changes, false);
        Boolean fowPlus = changes.get(FOWOption.FOW_PLUS);
        if (Boolean.TRUE.equals(fowPlus)) {
            sb.append("\nFoW+ also switches the explore deck to `")
                    .append(FOWPlusService.FOWPLUS_EXPLORE_DECK)
                    .append("` and changes play:\n")
                    .append(FOWPlusService.FOWPLUS_PLAY_CHANGES)
                    .append('\n');
        } else if (Boolean.FALSE.equals(fowPlus)) {
            sb.append("\nTurning FoW+ off puts back the explore deck the game had before FoW+, if the bot saved it.\n");
        }
        sb.append("\nGM turn map, Fog QoL 01, Status summary, the map layout options and the rules options")
                .append(" are only changed by presets that turn them on.");
        return sb.toString();
    }

    private static void appendTitles(StringBuilder sb, String label, Map<FOWOption, Boolean> changes, boolean value) {
        List<String> titles = changes.entrySet().stream()
                .filter(change -> change.getValue() == value)
                .map(change -> change.getKey().getTitle())
                .toList();
        if (!titles.isEmpty()) {
            sb.append(label).append(String.join(", ", titles)).append('\n');
        }
    }

    private static String appliedText(Game game, FogPreset preset, boolean fowPlusWasOn) {
        StringBuilder sb = new StringBuilder("### Fog preset applied: ")
                .append(preset.getTitle())
                .append('\n');
        if (preset.isFowPlus() && !fowPlusWasOn) {
            sb.append("**FoW+ changes in play:**\n")
                    .append(FOWPlusService.FOWPLUS_PLAY_CHANGES)
                    .append("\nExplore deck: `")
                    .append(game.getExplorationDeckID())
                    .append("`.\n");
        }
        if (!preset.isFowPlus() && fowPlusWasOn) {
            sb.append("FoW+ is off. Explore deck: `")
                    .append(game.getExplorationDeckID())
                    .append("`.\n");
        }
        return sb.append("Change single options with `/fow fow_options`.").toString();
    }

    public static String presetList() {
        return Stream.of(FogPreset.values())
                .map(preset -> "**" + preset.getTitle() + "**: " + preset.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
