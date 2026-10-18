package ti4.service.testbed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
public class TestBedAutoComplete {

    public static final String PRESET_OPTION = "preset";
    public static final String SCRIPT_OPTION = "script";
    public static final String ALL_SCRIPTS = "all";
    private static final int MAX_CHOICES = 25;
    private static final int MAX_NAME = 100;

    @Nullable
    public static List<Command.Choice> choices(String optionName, @Nullable Game game, String entered) {
        Map<String, String> labelsByValue = new LinkedHashMap<>();
        switch (optionName) {
            case PRESET_OPTION ->
                TestBedPresetService.loadPresets()
                        .forEach((name, preset) -> labelsByValue.put(name, label(name, preset.getDescription())));
            case SCRIPT_OPTION -> {
                labelsByValue.put(
                        ALL_SCRIPTS, label(ALL_SCRIPTS, "every script (shipped and yours), each from a fresh reset"));
                TestBedScriptService.loadScripts()
                        .forEach((name, script) -> labelsByValue.put(name, label(name, script.getDescription())));
            }
            case Constants.FACTION_COLOR -> {
                labelsByValue.put(
                        TestBedPanelService.ACT_AS_TURN,
                        label(TestBedPanelService.ACT_AS_TURN, "follow the active player"));
                if (game != null) {
                    for (Player seat : game.getRealPlayers()) {
                        labelsByValue.put(seat.getFaction(), seat.getFaction() + " (" + seat.getColor() + ")");
                    }
                }
            }
            default -> {
                return null;
            }
        }
        String filter = entered.toLowerCase(Locale.ROOT);
        List<Command.Choice> choices = new ArrayList<>();
        labelsByValue.forEach((value, label) -> {
            if (choices.size() < MAX_CHOICES && label.toLowerCase(Locale.ROOT).contains(filter)) {
                choices.add(new Command.Choice(label, value));
            }
        });
        return choices;
    }

    static String label(String name, @Nullable String description) {
        if (description == null || description.isBlank()) return StringUtils.left(name, MAX_NAME);
        return StringUtils.abbreviate(name + " — " + description, MAX_NAME);
    }
}
