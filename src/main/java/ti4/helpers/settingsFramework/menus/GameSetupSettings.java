package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.BooleanSettingWithCustomAction;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import tools.jackson.databind.JsonNode;

@Getter
public class GameSetupSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------

    // Settings
    private ListSetting<Player> gamePlayers;
    private final IntegerSetting pointTotal;
    private final IntegerSetting stage1s;
    private final IntegerSetting stage2s;
    private final IntegerSetting secrets;
    private final BooleanSettingWithCustomAction tigl;
    private final BooleanSettingWithCustomAction tiglFractured;
    private final BooleanSetting alliance;
    // Categories
    private final DeckSettings decks;
    // Bonus Attributes
    @JsonIgnore
    private final Game game;

    private static final String MENU_ID = "gameSetup";

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    GameSetupSettings(@NotNull Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Game setup settings", "Edit core game setup rules", parent);
        this.game = game;

        // Initialize Settings to default values
        int defaultVP = game.getVp();
        pointTotal = new IntegerSetting("Points", "Point Total", defaultVP, 1, 20, 1);
        stage1s = new IntegerSetting("Stage1s", "number of Stage 1 public objectives", 5, 1, 20, 1);
        stage2s = new IntegerSetting("Stage2s", "number of Stage 2 public objectives", 5, 1, 20, 1);
        secrets = new IntegerSetting("Secrets", "Max number of secret objectives", 3, 1, 10, 1);
        boolean defaultTigl = game.isCompetitiveTIGLGame();
        tigl = new BooleanSettingWithCustomAction(
                "TIGL", "TIGL Game", defaultTigl, (value) -> ensureTIGLConsistency(true, false));
        tiglFractured = new BooleanSettingWithCustomAction(
                "TIGL Fractured", "TIGL Fractured Game", false, (value) -> ensureTIGLConsistency(false, true));
        alliance = new BooleanSetting("Alliance", "Alliance Mode", false);

        // Initialize values & keys for gamePlayers
        Set<Entry<String, Player>> allPlayers = game.getPlayers().entrySet();
        Set<String> defaultPlayers =
                game.getPlayers().values().stream().map(Player::getUserID).collect(Collectors.toSet());
        Set<String> players =
                Optional.ofNullable(gamePlayers).map(ListSetting::getKeys).orElse(defaultPlayers);
        gamePlayers = new ListSetting<>(
                "Players", "Players playing", "Add player", "Remove player", allPlayers, players, defaultPlayers);

        // Emojis
        pointTotal.setEmoji(MiscEmojis.CustodiansVP);
        stage1s.setEmoji(CardEmojis.Public1);
        stage2s.setEmoji(CardEmojis.Public2);
        secrets.setEmoji(CardEmojis.SecretObjective);
        tigl.setEmoji(MiscEmojis.TIGL);
        tiglFractured.setEmoji(MiscEmojis.TIGL);
        alliance.setEmoji(SourceEmojis.StrategicAlliance);

        // Other init
        gamePlayers.setShow(Player::getUserName);

        // Load JSON if applicable
        if (json != null && json.has("menuId") && json.get("menuId").asText("").equals(MENU_ID)) {
            pointTotal.initialize(json.get("pointTotal"));
            stage1s.initialize(json.get("stage1s"));
            stage2s.initialize(json.get("stage2s"));
            secrets.initialize(json.get("secrets"));
            tigl.initialize(json.get("tigl"));
            tiglFractured.initialize(json.get("tiglFractured"));
            alliance.initialize(json.get("alliance"));
            gamePlayers.initialize(json.get("gamePlayers"));
        }

        decks = new DeckSettings(json, this, game);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    protected List<SettingsMenu> categories() {
        List<SettingsMenu> implemented = new ArrayList<>();
        implemented.add(decks);
        return implemented;
    }

    @Override
    protected List<SettingInterface> settings() {
        // implemented.add(draftMode);
        List<SettingInterface> settings = new ArrayList<>();
        settings.add(gamePlayers);
        settings.add(pointTotal);
        settings.add(stage1s);
        settings.add(stage2s);
        settings.add(secrets);
        settings.add(tigl);
        settings.add(tiglFractured);
        settings.add(alliance);
        return settings;
    }

    @Override
    protected List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";
        buttons.add(Buttons.red(prefix + "preset14pt", "Long War (14pt)"));
        buttons.add(Buttons.red(prefix + "preset444", "4/4/4 mode"));
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "preset14pt" -> preset14vp();
                    case "preset444" -> preset444();
                    default -> null;
                };
        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    private void ensureTIGLConsistency(boolean userToggleTIGL, boolean userToggleTIGLFractured) {
        if (userToggleTIGL) {
            boolean tiglStatus = tigl.isVal();
            if (!tiglStatus) {
                tiglFractured.setVal(false); // keep fractured off if TIGL is turned off
            }
        }
        if (userToggleTIGLFractured) {
            boolean fracturedStatus = tiglFractured.isVal();
            if (fracturedStatus) {
                tigl.setVal(true); // keep TIGL on if fractured is on
            }
        }
    }

    private String preset444() {
        pointTotal.setVal(12);
        stage1s.setVal(4);
        stage2s.setVal(4);
        secrets.setVal(4);
        return null;
    }

    private String preset14vp() {
        pointTotal.setVal(14);
        stage1s.setVal(5);
        stage2s.setVal(5);
        secrets.setVal(3);
        return null;
    }
}
