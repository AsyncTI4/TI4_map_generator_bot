package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;

// This is a sub-menu
@Getter
@JsonIgnoreProperties({ "messageId" })
public class SourceSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final BooleanSetting base;
    private final BooleanSetting pok;
    private final BooleanSetting codexes;
    private final BooleanSetting discoStars;
    private final BooleanSetting unchartedSpace;
    private final BooleanSetting absol;
    private final BooleanSetting miltymod;
    private final BooleanSetting eronous;
    private BooleanSetting cryypter;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public SourceSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("source", "Expansions and Homebrew", "Adjust various settings related to expansions and homebrew that you wish to use", parent);

        // Initialize Settings to default values
        base = new BooleanSetting("BaseGame", "Base Game", true);
        pok = new BooleanSetting("PoK", "Prophecy of Kings", true);
        codexes = new BooleanSetting("Codexes", "Codex 1-3", true);
        discoStars = new BooleanSetting("DiscoStars", "DS Factions", false);
        unchartedSpace = new BooleanSetting("UnchartSpace", "Uncharted Space", false);
        absol = new BooleanSetting("Absol", "Absol Mod", false);
        miltymod = new BooleanSetting("MiltyMod", "Milty Mod", false);
        eronous = new BooleanSetting("Eronous", "Eronous Tiles", false);
        // cryypter = new BooleanSetting("Cryypter", "Voices of the Council", false);

        // Emojis
        base.setEmoji(SourceEmojis.TI4BaseGame);
        pok.setEmoji(SourceEmojis.TI4PoK);
        codexes.setEmoji(FactionEmojis.Keleres);
        discoStars.setEmoji(SourceEmojis.DiscordantStars);
        unchartedSpace.setEmoji(SourceEmojis.DiscordantStars);
        absol.setEmoji(SourceEmojis.Absol);
        miltymod.setEmoji(SourceEmojis.MiltyMod);
        eronous.setEmoji(SourceEmojis.Eronous);

        // Other Initialization
        miltymod.setExtraInfo("NOTE: this is NOT \"milty draft\", this is a homebrew mod that replaces components in the game");

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("sourceSettings")) json = json.get("sourceSettings");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("source"));
        if (json != null && json.has("menuId") && historicIDs.contains(json.get("menuId").asText(""))) {
            base.initialize(json.get("base"));
            pok.initialize(json.get("pok"));
            codexes.initialize(json.get("codexes"));
            discoStars.initialize(json.get("discoStars"));
            unchartedSpace.initialize(json.get("unchartedSpace"));
            absol.initialize(json.get("absol"));
            miltymod.initialize(json.get("miltymod"));
            eronous.initialize(json.get("eronous"));
            // cryypter.initialize(json.get("voice_of_the_council"));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        // Add settings to the list. Any marked 'disabled' will not show
        ls.add(base);
        ls.add(pok);
        ls.add(codexes);
        ls.add(discoStars);
        ls.add(unchartedSpace);
        ls.add(absol);
        ls.add(miltymod);
        ls.add(eronous);
        // ls.add(cryypter);
        return ls;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation / Helpers
    // ---------------------------------------------------------------------------------------------------------------------------------
    @JsonIgnore
    public List<ComponentSource> getTileSources() {
        List<ComponentSource> sources = new ArrayList<>();
        if (base.isVal()) sources.add(ComponentSource.base);
        if (pok.isVal()) sources.add(ComponentSource.pok);
        if (codexes.isVal()) sources.addAll(List.of(ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3));
        if (unchartedSpace.isVal()) sources.add(ComponentSource.uncharted_space);
        if (absol.isVal()) sources.add(ComponentSource.absol);
        if (miltymod.isVal()) sources.add(ComponentSource.miltymod);
        if (eronous.isVal()) sources.add(ComponentSource.eronous);
        //if (cryypter.isVal()) sources.add(ComponentSource.cryypter);
        return sources;
    }

    @JsonIgnore
    public List<ComponentSource> getFactionSources() {
        List<ComponentSource> sources = new ArrayList<>();
        if (base.isVal()) sources.add(ComponentSource.base);
        if (pok.isVal()) sources.add(ComponentSource.pok);
        if (codexes.isVal()) sources.addAll(List.of(ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3));
        if (discoStars.isVal()) sources.add(ComponentSource.ds);
        if (absol.isVal()) sources.add(ComponentSource.absol);
        if (miltymod.isVal()) sources.add(ComponentSource.miltymod);
        if (eronous.isVal()) sources.add(ComponentSource.eronous);
        //if (cryypter.isVal()) sources.add(ComponentSource.cryypter);
        return sources;
    }
}
