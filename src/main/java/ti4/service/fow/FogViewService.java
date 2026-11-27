package ti4.service.fow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.map.CompactOverviewGenerator;
import ti4.message.componentsV2.ComponentPanel;
import ti4.message.componentsV2.PanelView;

@UtilityClass
public class FogViewService {

    static final String GHOSTS_BUTTON = "fogViewGhosts";
    static final String LAYOUT_BUTTON = "fogViewLayout";
    static final String FILTER_SELECT = "fogViewFilter";
    static final String CHOICE_BUTTON = "fogViewPick_";
    private static final String LAYOUT = "layout";
    private static final String LABELS = "labels";
    private static final String ISLANDS = "islands";
    private static final String GHOSTS = "ghosts";
    private static final String HEADER = "### Your fog view\n-# Only for you; applies to all your fog games.";
    private static final Map<String, String> SUFFIX_BY_LABEL = filtersByLabel();
    private static final Map<Integer, String> REACH_LABELS = reachLabels();

    private static Map<Integer, String> reachLabels() {
        Map<Integer, String> labels = new LinkedHashMap<>();
        labels.put(1, "Touching");
        labels.put(2, "1 empty hex");
        labels.put(3, "2 empty hexes");
        labels.put(4, "3 empty hexes");
        labels.put(CompactOverviewGenerator.Options.UNLIMITED_REACH, "Whole segments");
        return labels;
    }

    public static Map<Integer, String> reachChoices() {
        return REACH_LABELS;
    }

    public static List<String> filterLabels() {
        return List.copyOf(SUFFIX_BY_LABEL.keySet());
    }

    @Nullable
    public static String filterSuffixFor(String input) {
        String wanted = input.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> filter : SUFFIX_BY_LABEL.entrySet()) {
            if (filter.getKey().toLowerCase(Locale.ROOT).equals(wanted)
                    || filter.getValue().equals(wanted)) {
                return filter.getValue();
            }
        }
        return switch (wanted) {
            case "darkgrey", "dark grey", "grey", "gray" -> "default";
            default -> null;
        };
    }

    static String filterLabelFor(String suffix) {
        return SUFFIX_BY_LABEL.entrySet().stream()
                .filter(filter -> filter.getValue().equals(suffix))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(Constants.FOW_FILTER_DARK_GREY);
    }

    private static Map<String, String> filtersByLabel() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put(Constants.FOW_FILTER_DARK_GREY, "default");
        filters.put(Constants.FOW_FILTER_SEPIA, "sepia");
        filters.put(Constants.FOW_FILTER_WHITE, "white");
        filters.put(Constants.FOW_FILTER_PINK, "pink");
        filters.put(Constants.FOW_FILTER_PURPLE, "purple");
        filters.put(Constants.FOW_FILTER_FROG, "frog");
        return filters;
    }

    public static void post(Player player, MessageChannel channel, @Nullable String notice) {
        ComponentPanel.post(channel, components(player, notice));
    }

    static List<MessageTopLevelComponent> components(Player player, @Nullable String notice) {
        return ComponentPanel.compose(HEADER, notice, view(player), List.of());
    }

    private static PanelView view(Player player) {
        String owner = player.factionButtonChecker();
        List<ContainerChildComponent> body = new ArrayList<>();
        addChoices(
                body,
                owner,
                "Overview layout",
                "How the Overview button packs the map.",
                LAYOUT,
                player.isFogOverviewBoxes() ? "boxes" : "hexes",
                choices("hexes", "Hexes", "boxes", "Boxes"));
        addChoices(
                body,
                owner,
                "Overview labels",
                "Full segment names on the map, or short codes (M1, A2, …) with a legend.",
                LABELS,
                player.isFogOverviewCodes() ? "codes" : "names",
                choices("names", "Names", "codes", "Codes + legend"));
        addChoices(
                body,
                owner,
                "Overview islands",
                "Known systems this close stay together as one island.",
                ISLANDS,
                String.valueOf(selectedReach(player.getFogOverviewReach())),
                islandChoices());
        addChoices(
                body,
                owner,
                "Ghost hexes",
                "Faint numbered rings on unexplored hexes next to known space, when the game uses them.",
                GHOSTS,
                player.isFogGhostHexes() ? "on" : "off",
                choices("on", "On", "off", "Off"));
        body.add(TextDisplay.of("**Fog tile colour**\n-# Colour of the systems you cannot see right now."));
        body.add(ActionRow.of(filterMenu(owner, player.getFogFilter())));
        return PanelView.of(body, List.of());
    }

    private static void addChoices(
            List<ContainerChildComponent> body,
            String owner,
            String title,
            String hint,
            String setting,
            String current,
            Map<String, String> choices) {
        body.add(TextDisplay.of("**" + title + "**\n-# " + hint));
        body.add(ActionRow.of(choices.entrySet().stream()
                .map(choice -> choice.getKey().equals(current)
                        ? Buttons.green(choiceId(owner, setting, choice.getKey()), "✓ " + choice.getValue())
                        : Buttons.gray(choiceId(owner, setting, choice.getKey()), choice.getValue()))
                .toList()));
    }

    private static Map<String, String> choices(String first, String firstLabel, String second, String secondLabel) {
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put(first, firstLabel);
        choices.put(second, secondLabel);
        return choices;
    }

    private static Map<String, String> islandChoices() {
        Map<String, String> choices = new LinkedHashMap<>();
        REACH_LABELS.forEach((reach, label) -> choices.put(String.valueOf(reach), label));
        return choices;
    }

    static String choiceId(String owner, String setting, String value) {
        return owner + CHOICE_BUTTON + setting + "_" + value;
    }

    private static int selectedReach(int reach) {
        return REACH_LABELS.containsKey(reach) ? reach : CompactOverviewGenerator.Options.DEFAULT_REACH;
    }

    private static StringSelectMenu filterMenu(String owner, String current) {
        List<SelectOption> options = SUFFIX_BY_LABEL.entrySet().stream()
                .map(filter -> SelectOption.of(filter.getKey(), filter.getValue())
                        .withDefault(filter.getValue().equals(current)))
                .toList();
        return StringSelectMenu.create(owner + FILTER_SELECT)
                .setPlaceholder("Fog tile colour")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    @ButtonHandler(GHOSTS_BUTTON)
    static void toggleGhosts(ButtonInteractionEvent event, Game game, Player player) {
        player.setFogGhostHexes(!player.isFogGhostHexes());
        ComponentPanel.show(event, components(player, null));
    }

    @ButtonHandler(LAYOUT_BUTTON)
    static void toggleLayout(ButtonInteractionEvent event, Game game, Player player) {
        player.setFogOverviewBoxes(!player.isFogOverviewBoxes());
        ComponentPanel.show(event, components(player, null));
    }

    @ButtonHandler(CHOICE_BUTTON)
    static void pickChoice(ButtonInteractionEvent event, Game game, Player player) {
        String choice = StringUtils.substringAfter(event.getButton().getCustomId(), CHOICE_BUTTON);
        apply(player, StringUtils.substringBefore(choice, "_"), StringUtils.substringAfter(choice, "_"));
        ComponentPanel.show(event, components(player, null));
    }

    static void apply(Player player, String setting, String value) {
        switch (setting) {
            case LAYOUT -> player.setFogOverviewBoxes("boxes".equals(value));
            case LABELS -> player.setFogOverviewCodes("codes".equals(value));
            case ISLANDS -> {
                if (value.matches("\\d")) player.setFogOverviewReach(Integer.parseInt(value));
            }
            case GHOSTS -> player.setFogGhostHexes("on".equals(value));
            default -> {}
        }
    }

    @SelectionHandler(FILTER_SELECT)
    static void pickFilter(StringSelectInteractionEvent event, Game game, Player player) {
        if (event.getValues().isEmpty()) return;
        String suffix = filterSuffixFor(event.getValues().getFirst());
        if (suffix != null) {
            player.setFogFilter(suffix);
        }
        ComponentPanel.show(event, components(player, null));
    }
}
