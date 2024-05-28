package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SourceSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private BooleanSetting base, pok, codexes, discoStars, unchartedSpace, absol, miltymod, eronous;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void finishInitialization(Game game, SettingsMenu parent) {
        // Required "Static" Attributes
        this.menuId = "source";
        this.menuName = "Expansions and Homebrew";
        this.description = "Adjust various settings related to expansions and homebrew that you want to use";

        base = new BooleanSetting("BaseGame", "Base Game", true, base);
        pok = new BooleanSetting("PoK", "Prophecy of Kings", true, pok);
        codexes = new BooleanSetting("Codexes", "Codex 1-3", true, codexes);
        discoStars = new BooleanSetting("DiscoStars", "DS Factions", false, discoStars);
        unchartedSpace = new BooleanSetting("UnchartSpace", "Uncharted Space", false, unchartedSpace);
        absol = new BooleanSetting("Absol", "Absol Mod", false, absol);
        miltymod = new BooleanSetting("MiltyMod", "Milty Mod", false, miltymod);
        eronous = new BooleanSetting("Eronous", "Eronous Tiles", false, eronous);

        // Emojis
        base.setEmoji(Emojis.TI4BaseGame);
        pok.setEmoji(Emojis.TI4PoK);
        codexes.setEmoji(Emojis.Keleres);
        discoStars.setEmoji(Emojis.DiscordantStars);
        unchartedSpace.setEmoji(Emojis.DiscordantStars);
        absol.setEmoji(Emojis.Absol);
        miltymod.setEmoji(Emojis.MiltyMod);
        eronous.setEmoji(Emojis.Eronous);

        miltymod.setExtraInfo("NOTE: this is NOT \"milty draft\", this is a homebrew mod that replaces components in the game");
        super.finishInitialization(game, parent);
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<SettingInterface>();
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
        if (base.val) sources.add(ComponentSource.base);
        if (pok.val) sources.add(ComponentSource.pok);
        if (codexes.val) sources.addAll(List.of(ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3));
        if (unchartedSpace.val) sources.add(ComponentSource.ds);
        if (absol.val) sources.add(ComponentSource.absol);
        if (miltymod.val) sources.add(ComponentSource.miltymod);
        if (eronous.val) sources.add(ComponentSource.eronous);
        return sources;
    }

    @JsonIgnore
    public List<ComponentSource> getFactionSources() {
        List<ComponentSource> sources = new ArrayList<>();
        if (base.val) sources.add(ComponentSource.base);
        if (pok.val) sources.add(ComponentSource.pok);
        if (codexes.val) sources.addAll(List.of(ComponentSource.codex1, ComponentSource.codex2, ComponentSource.codex3));
        if (discoStars.val) sources.add(ComponentSource.ds);
        if (absol.val) sources.add(ComponentSource.absol);
        if (miltymod.val) sources.add(ComponentSource.miltymod);
        if (eronous.val) sources.add(ComponentSource.eronous);
        return sources;
    }

}
