package ti4.service.option;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.logging.BotLogger;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.GMService;

@UtilityClass
public class FOWOptionService {

    private static final Pattern FOW_OPTION_ = Pattern.compile("fowOption_");
    private static final int BUTTONS_PER_ROW = 5;

    public enum FOWOptionCategory {
        GAME,
        VISIBILITY,
        OTHER;

        static FOWOptionCategory fromString(String string) {
            for (FOWOptionCategory category : values()) {
                if (category.name().equalsIgnoreCase(string)) {
                    return category;
                }
            }
            throw new IllegalArgumentException("No FOWOptionCategory enum for '" + string + "'");
        }
    }

    public enum FOWOption {
        // Comm Options
        MANAGED_COMMS(FOWOptionCategory.GAME, "Managed comms", "Use managed player-to-player communication threads"),
        ALLOW_AGENDA_COMMS(
                FOWOptionCategory.GAME,
                "Allow comms in agenda",
                "Managed player-to-player communication threads allow talking with everyone in Agenda Phase"),
        STATUS_SUMMARY(
                FOWOptionCategory.GAME, "Status summary", "Prints explores info as summary thread in status homework"),
        HIDE_TOTAL_VOTES(FOWOptionCategory.GAME, "Hide total votes", "Hide total votes amount in agenda"),
        HIDE_VOTE_ORDER(FOWOptionCategory.GAME, "Hide voting order", "Hide player colors from vote order"),

        // Visibility Options
        BRIGHT_NOVAS(FOWOptionCategory.VISIBILITY, "Bright Novas", "Locations of Supernovas are always visible"),
        HIDE_EXPLORES(
                FOWOptionCategory.VISIBILITY, "Hide Explore Decks", "Disables looking at explore and relic decks"),
        HIDE_MAP(FOWOptionCategory.VISIBILITY, "Hide Unexplored Map", "Hides unexplored (blue 0b) map tiles."),
        HIDE_PLAYER_INFOS(
                FOWOptionCategory.VISIBILITY, "Hide Player Infos", "Hides anchored player info areas from the map."),
        STATS_FROM_HS_ONLY(
                FOWOptionCategory.VISIBILITY,
                "Stats from HS",
                "Only way to see players stats is to see their Home System"),
        HIDE_AC_DISCARD(
                FOWOptionCategory.VISIBILITY,
                "Hide AC Discard",
                "Action card discard pile shows only cards that were played"),

        // Other Options
        HIDE_PLAYER_NAMES(
                FOWOptionCategory.OTHER, "Hide real names", "Completely hide player Discord names on the map"),
        DISABLE_FRACTURE(
                FOWOptionCategory.OTHER,
                "Disable Fracture",
                "The Fracture can never enter play. Same setting as `/game weird_game_setup no_fracture`"),

        // Hidden from normal options
        FOW_PLUS(null, "FoW Plus Mode", "Hello darkness my old friend... WIP - ask Solax for details", false),
        RIFTSET_MODE(null, "RiftSet Mode", "For Eronous to run fow300", false);

        private final FOWOptionCategory category;

        @Getter
        private final String title;

        private final String description;
        private final boolean visible;

        FOWOption(FOWOptionCategory category, String title, String description) {
            this(category, title, description, true);
        }

        FOWOption(FOWOptionCategory category, String title, String description, boolean visible) {
            this.category = category;
            this.title = title;
            this.description = description;
            this.visible = visible;
        }

        FOWOptionCategory getCategory() {
            return category;
        }

        String getDescription() {
            return description;
        }

        boolean isVisible() {
            return visible;
        }

        public static FOWOption fromString(String value) {
            for (FOWOption option : values()) {
                if (option.name().equalsIgnoreCase(value)) {
                    return option;
                }
            }
            throw new IllegalArgumentException("No FOWOption enum for '" + value + "'");
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static void offerFOWOptionButtons(Game game) {
        offerFOWOptionButtons(null, game, FOWOptionCategory.GAME);
    }

    private static void offerFOWOptionButtons(
            ButtonInteractionEvent event, Game game, FOWOptionCategory selectedCategory) {
        StringBuilder sb = new StringBuilder("### Change FoW " + selectedCategory + " Options\n\n");
        if (FOWPlusService.isActive(game)) {
            sb.append("_FoW+ mode is active. Some options are forced and cannot be changed._\n\n");
        }

        List<ActionRow> rows = new ArrayList<>();
        List<Button> categoryButtons = new ArrayList<>();
        for (FOWOptionCategory category : FOWOptionCategory.values()) {
            if (category == selectedCategory) {
                categoryButtons.add(Buttons.gray("fowOptionCategory_" + category, category.name() + " Options"));
            } else {
                categoryButtons.add(Buttons.blue("fowOptionCategory_" + category, category.name() + " Options"));
            }
        }
        categoryButtons.add(Buttons.gray("deleteButtons", "Done"));

        List<Button> optionButtons = new ArrayList<>();
        for (FOWOption option : FOWOption.values()) {
            if (!option.isVisible() || option.getCategory() != selectedCategory) {
                continue;
            }

            boolean currentValue = readOption(game, option);
            sb.append(valueRepresentation(currentValue))
                    .append(" **")
                    .append(option.getTitle())
                    .append("**\n");
            sb.append("-# ").append(option.getDescription()).append('\n');

            if (FOWPlusService.isActive(game)
                    && FOWPlusService.FORCED_FOWPLUS_OPTIONS.stream().anyMatch(p -> p.getLeft() == option)) {
                continue;
            }
            optionButtons.add(
                    currentValue
                            ? Buttons.red(
                                    "fowOption_" + selectedCategory + "_false_" + option,
                                    "Disable " + option.getTitle())
                            : Buttons.green(
                                    "fowOption_" + selectedCategory + "_true_" + option,
                                    "Enable " + option.getTitle()));
        }

        // An ActionRow holds at most 5 buttons, and the message at most 5 rows - one of which is the category row.
        for (int i = 0; i < optionButtons.size(); i += BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(optionButtons.subList(i, Math.min(i + BUTTONS_PER_ROW, optionButtons.size()))));
        }
        rows.add(ActionRow.of(categoryButtons));

        if (event == null) {
            GMService.getGMChannel(game)
                    .sendMessage(sb.toString())
                    .addComponents(rows)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            event.getHook()
                    .editOriginal(sb.toString())
                    .setComponents(rows)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler("fowOption_")
    public static void changeFOWOptions(ButtonInteractionEvent event, Game game, String buttonID) {
        String[] parts = FOW_OPTION_.split(buttonID)[1].split("_", 3);
        FOWOptionCategory category = FOWOptionCategory.fromString(parts[0]);
        String value = parts[1];
        String option = parts[2];

        FOWOption fowOption = FOWOption.fromString(option);
        boolean newValue = Boolean.parseBoolean(value);
        writeOption(game, fowOption, newValue);
        offerFOWOptionButtons(event, game, category);
    }

    /** DISABLE_FRACTURE reads/writes noFractureMode, so this and {@code weird_game_setup no_fracture} cannot diverge. */
    private static boolean readOption(Game game, FOWOption option) {
        if (option == FOWOption.DISABLE_FRACTURE) return game.isNoFractureMode();
        return game.getFowOption(option);
    }

    private static void writeOption(Game game, FOWOption option, boolean value) {
        if (option == FOWOption.DISABLE_FRACTURE) {
            game.setNoFractureMode(value);
        } else {
            game.setFowOption(option, value);
        }
    }

    @ButtonHandler("fowOptionCategory_")
    public static void changeFOWOptionsCategory(ButtonInteractionEvent event, Game game, String buttonID) {
        FOWOptionCategory category = FOWOptionCategory.fromString(buttonID.replace("fowOptionCategory_", ""));
        offerFOWOptionButtons(event, game, category);
    }

    public static String valueRepresentation(boolean value) {
        return value ? "✅" : "🚫";
    }
}
