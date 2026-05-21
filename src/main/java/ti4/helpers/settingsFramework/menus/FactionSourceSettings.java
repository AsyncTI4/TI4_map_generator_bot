package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import tools.jackson.databind.JsonNode;

@Getter
@JsonIgnoreProperties("messageId")
public class FactionSourceSettings extends SettingsMenu {

    private static final List<ComponentSource> LOGICAL_SOURCES = List.of(
            ComponentSource.base,
            ComponentSource.pok,
            ComponentSource.ds,
            ComponentSource.thunders_edge,
            ComponentSource.eronous,
            ComponentSource.ignis_aurora,
            ComponentSource.balacasi);

    private final IntegerRangeSetting base;
    private final IntegerRangeSetting pok;
    private final IntegerRangeSetting ds;
    private final IntegerRangeSetting thundersEdge;
    private final IntegerRangeSetting eronous;
    private final IntegerRangeSetting ignisAurora;
    private final IntegerRangeSetting whispers;

    FactionSourceSettings(JsonNode json, SettingsMenu parent) {
        super(
                "factionSources",
                "Faction Expansion Limits",
                "Set min/max factions from each expansion in the draft pool",
                parent);

        int baseCount = countFactions(ComponentSource.base);
        int pokCount = countFactions(
                ComponentSource.pok,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4);
        int dsCount = countFactions(ComponentSource.ds);
        int teCount = countFactions(ComponentSource.thunders_edge);
        int eronousCount = countFactions(ComponentSource.eronous);
        int ignisCount = countFactions(ComponentSource.ignis_aurora);
        int balacasiCount = countFactions(ComponentSource.balacasi);

        base = new IntegerRangeSetting(
                "FactionBase", "Base Game factions", 0, 0, baseCount, baseCount, 0, baseCount, 1);
        pok = new IntegerRangeSetting("FactionPoK", "PoK factions", 0, 0, pokCount, pokCount, 0, pokCount, 1);
        ds = new IntegerRangeSetting("FactionDS", "Discordant Stars factions", 0, 0, dsCount, dsCount, 0, dsCount, 1);
        thundersEdge =
                new IntegerRangeSetting("FactionTE", "Thunder's Edge factions", 0, 0, teCount, teCount, 0, teCount, 1);
        eronous = new IntegerRangeSetting(
                "FactionEronous", "Eronous factions", 0, 0, eronousCount, eronousCount, 0, eronousCount, 1);
        ignisAurora = new IntegerRangeSetting(
                "FactionIgnis", "Ignis Aurora factions", 0, 0, ignisCount, ignisCount, 0, ignisCount, 1);
        whispers = new IntegerRangeSetting(
                "FactionWhispers",
                "Whispers from the Void factions",
                0,
                0,
                balacasiCount,
                balacasiCount,
                0,
                balacasiCount,
                1);

        if (json != null && json.has("factionSourceSettings")) json = json.get("factionSourceSettings");
        if (json != null
                && json.has("menuId")
                && "factionSources".equals(json.get("menuId").asString(""))) {
            base.initialize(json.get("base"));
            pok.initialize(json.get("pok"));
            ds.initialize(json.get("ds"));
            thundersEdge.initialize(json.get("thundersEdge"));
            eronous.initialize(json.get("eronous"));
            ignisAurora.initialize(json.get("ignisAurora"));
            whispers.initialize(json.get("whispers"));
        }
    }

    @Override
    boolean showInParentSummary() {
        return false;
    }

    @Override
    public List<SettingInterface> settings() {
        MiltySettings ms = getMiltySettings();
        if (ms == null) return List.of();
        List<ComponentSource> enabled = ms.getSourceSettings().getFactionSources();
        return LOGICAL_SOURCES.stream()
                .filter(s -> isEnabled(enabled, s))
                .map(this::settingFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Map<ComponentSource, int[]> getConstraintMap() {
        MiltySettings ms = getMiltySettings();
        if (ms == null) return Map.of();
        List<ComponentSource> enabled = ms.getSourceSettings().getFactionSources();
        Map<ComponentSource, int[]> map = new EnumMap<>(ComponentSource.class);
        for (ComponentSource src : LOGICAL_SOURCES) {
            if (!isEnabled(enabled, src)) continue;
            IntegerRangeSetting s = settingFor(src);
            if (s != null) map.put(src, new int[] {s.getValLow(), s.getValHigh()});
        }
        return map;
    }

    private boolean isEnabled(List<ComponentSource> enabled, ComponentSource logical) {
        if (logical == ComponentSource.pok)
            return enabled.contains(ComponentSource.pok)
                    || enabled.stream().anyMatch(s -> s.name().startsWith("codex"));
        return enabled.contains(logical);
    }

    private IntegerRangeSetting settingFor(ComponentSource logical) {
        return switch (logical) {
            case base -> base;
            case pok -> pok;
            case ds -> ds;
            case thunders_edge -> thundersEdge;
            case eronous -> eronous;
            case ignis_aurora -> ignisAurora;
            case balacasi -> whispers;
            default -> null;
        };
    }

    private MiltySettings getMiltySettings() {
        if (parent instanceof PlayerFactionSettings pfs && pfs.parent instanceof MiltySettings ms) {
            return ms;
        }
        return null;
    }

    private static int countFactions(ComponentSource... sources) {
        List<ComponentSource> sourceList = List.of(sources);
        return (int) Mapper.getFactionsValues().stream()
                .filter(f -> sourceList.contains(f.getSource()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("neutral"))
                .filter(f -> !f.getAlias().contains("keleres") || "keleresm".equals(f.getAlias()))
                .count();
    }
}
