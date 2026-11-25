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
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.componentsV2.ComponentPanel;
import ti4.message.componentsV2.PanelView;

@UtilityClass
public class FogViewService {

    static final String GHOSTS_BUTTON = "fogViewGhosts";
    static final String LAYOUT_BUTTON = "fogViewLayout";
    static final String FILTER_SELECT = "fogViewFilter";
    private static final String HEADER = "### Your fog view\n-# Only for you, only in this game.";
    private static final Map<String, String> SUFFIX_BY_LABEL = filtersByLabel();

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
        boolean ghosts = player.isFogGhostHexes();
        boolean boxes = player.isFogOverviewBoxes();
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(ComponentPanel.toggleSection(
                "Ghost hexes",
                "Faint numbered rings on unexplored hexes next to known space, when the game uses them.",
                ghosts,
                ComponentPanel.toggleButton(owner + GHOSTS_BUTTON, ghosts)));
        body.add(ComponentPanel.settingRow(
                "Overview layout",
                boxes ? "Boxes" : "Hexes",
                "How the Overview button packs the map: outlined hex shapes or boxes.",
                Buttons.blue(owner + LAYOUT_BUTTON, boxes ? "Use hexes" : "Use boxes")));
        body.add(TextDisplay.of("**Fog tile colour:** " + filterLabelFor(player.getFogFilter())
                + "\n-# Colour of the systems you cannot see right now. Pick one below."));
        return PanelView.of(body, List.of(ActionRow.of(filterMenu(owner, player.getFogFilter()))));
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
