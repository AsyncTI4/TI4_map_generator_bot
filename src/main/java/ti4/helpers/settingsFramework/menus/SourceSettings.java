package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.menus.MiltySettings.DraftingMode;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
public class SourceSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private BooleanSetting base, pok, codexes, discoStars, unchartedSpace, absol, miltymod, eronous;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public SourceSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("source", "Expansions and Homebrew", "Adjust various settings related to expansions and homebrew that you want to use", parent);

        // Initialize Settings to default values
        base = new BooleanSetting("BaseGame", "Base Game", true);
        pok = new BooleanSetting("PoK", "Prophecy of Kings", true);
        codexes = new BooleanSetting("Codexes", "Codex 1-3", true);
        discoStars = new BooleanSetting("DiscoStars", "DS Factions", false);
        unchartedSpace = new BooleanSetting("UnchartSpace", "Uncharted Space", false);
        absol = new BooleanSetting("Absol", "Absol Mod", false);
        miltymod = new BooleanSetting("MiltyMod", "Milty Mod", false);
        eronous = new BooleanSetting("Eronous", "Eronous Tiles", false);

        // Emojis
        base.setEmoji(Emojis.TI4BaseGame);
        pok.setEmoji(Emojis.TI4PoK);
        codexes.setEmoji(Emojis.Keleres);
        discoStars.setEmoji(Emojis.DiscordantStars);
        unchartedSpace.setEmoji(Emojis.DiscordantStars);
        absol.setEmoji(Emojis.Absol);
        miltymod.setEmoji(Emojis.MiltyMod);
        eronous.setEmoji(Emojis.Eronous);

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
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<SettingInterface>();

        // Over-Validation for Twilight Falls
        if (parent instanceof MiltySettings milty && milty.getDraftMode().getValue() == DraftingMode.twilightfalls) {
            codexes.setVal(false);
            discoStars.setVal(false);
            absol.setVal(false);
            miltymod.setVal(false);

            codexes.setDisabled(true);
            discoStars.setDisabled(true);
            absol.setDisabled(true);
            miltymod.setDisabled(true);
            if (getDescription().size() == 1)
                getDescription().add("\n - Note: These sources will only be used to determine tiles on the map.");
        } else {
            codexes.setDisabled(false);
            discoStars.setDisabled(false);
            absol.setDisabled(false);
            miltymod.setDisabled(false);
            while (getDescription().size() > 1)
                getDescription().remove(1);
        }

        // Add settings to the list. Any marked 'disabled' will not show
        ls.add(base);
        ls.add(pok);
        ls.add(codexes);
        ls.add(discoStars);
        ls.add(unchartedSpace);
        ls.add(absol);
        ls.add(miltymod);
        ls.add(eronous);
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
        return sources;
    }
}
