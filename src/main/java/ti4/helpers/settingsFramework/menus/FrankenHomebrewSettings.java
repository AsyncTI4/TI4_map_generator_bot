package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.service.emoji.SourceEmojis;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
class FrankenHomebrewSettings extends SettingsMenu {

    private final BooleanSetting discoStars;
    private final BooleanSetting blueReverie;
    private final BooleanSetting unchartedSpace;
    private final BooleanSetting eronous;

    FrankenHomebrewSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(
                "homebrewSettings",
                "Franken Homebrew",
                "Adjust the homebrew options used for this Franken draft",
                parent);

        discoStars = new BooleanSetting("DiscoStars", "Discordant Stars", game.isDiscordantStarsMode());
        blueReverie = new BooleanSetting("BlueReverie", "Blue Reverie", game.isBlueReverieMode());
        unchartedSpace = new BooleanSetting("UnchartSpace", "Uncharted Space", game.isUnchartedSpaceStuff());
        eronous = new BooleanSetting("Eronous", "Eronous Tiles", false);

        discoStars.setEmoji(SourceEmojis.DiscordantStars);
        unchartedSpace.setEmoji(SourceEmojis.DiscordantStars);
        eronous.setEmoji(SourceEmojis.Eronous);
        discoStars.setExtraInfo("Adds Discordant Stars faions only.");
        blueReverie.setExtraInfo("Adds Blue Reverie factions only.");
        unchartedSpace.setExtraInfo("Adds Uncharted Space content.");

        if (json != null && json.has("homebrewSettings")) json = json.get("homebrewSettings");
        if (json != null
                && json.has("menuId")
                && "homebrewSettings".equals(json.get("menuId").asString(""))) {
            discoStars.initialize(json.get("discoStars"));
            blueReverie.initialize(json.get("blueReverie"));
            unchartedSpace.initialize(json.get("unchartedSpace"));
            eronous.initialize(json.get("eronous"));
        }
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        if (parent instanceof FrankenSettings fs) {
            fs.syncFrankendrazDsBrState(lastSettingTouched);
        }
        return super.menuSummaryString(lastSettingTouched) + homebrewNotes();
    }

    @Override
    protected List<SettingInterface> settings() {
        return new ArrayList<>(List.of(discoStars, blueReverie, unchartedSpace, eronous));
    }

    @Override
    protected String resetSettings() {
        String err = super.resetSettings();
        if (err != null) return err;
        if (parent instanceof FrankenSettings fs && fs.isFrankendrazMode()) {
            discoStars.setVal(true);
            blueReverie.setVal(true);
        }
        return null;
    }

    private String homebrewNotes() {
        StringBuilder sb = new StringBuilder("""

                > These toggles determine which homebrew Franken will use when the draft starts.

                > Select "New PoK", "Old PoK", or "Thunder's Edge + New PoK" above to set official factions.
                """);

        if (parent instanceof FrankenSettings fs && fs.isFrankendrazMode()) {
            sb.append("""

                > Discordant Stars and Blue Reverie are enabled by default in FrankenDraz. Disable them using the "Disable DS Factions" and "Disable BR Factions" toggles in the main menu.
                > **NOTE**: If you do not have DS/BR on, the max amount of factions per player is 5 for a 6 player game and 7 for a 4 player game.""");
        }
        return sb.toString();
    }
}
