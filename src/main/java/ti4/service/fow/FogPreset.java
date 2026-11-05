package ti4.service.fow;

import static ti4.service.option.FOWOptionService.FOWOption.BRIGHT_NOVAS;
import static ti4.service.option.FOWOptionService.FOWOption.CLASSIC_MAP_LAYOUT;
import static ti4.service.option.FOWOptionService.FOWOption.DISABLE_FRACTURE;
import static ti4.service.option.FOWOptionService.FOWOption.FOG_QOL_01;
import static ti4.service.option.FOWOptionService.FOWOption.FOG_SECRETS;
import static ti4.service.option.FOWOptionService.FOWOption.FRACTURE_SEPARATE_MAP;
import static ti4.service.option.FOWOptionService.FOWOption.GHOST_HEXES;
import static ti4.service.option.FOWOptionService.FOWOption.GM_TURN_MAP;
import static ti4.service.option.FOWOptionService.FOWOption.HIDE_AC_DISCARD;
import static ti4.service.option.FOWOptionService.FOWOption.MANAGED_COMMS;
import static ti4.service.option.FOWOptionService.FOWOption.MAP_CONNECTIONS;
import static ti4.service.option.FOWOptionService.FOWOption.NEW_TRANSACTIONS;
import static ti4.service.option.FOWOptionService.FOWOption.OPTIONAL_PILLAGABLE_TG;
import static ti4.service.option.FOWOptionService.FOWOption.STATUS_SUMMARY;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import ti4.service.option.FOWOptionService.FOWOption;

@Getter
public enum FogPreset {
    STANDARD(
            "standard",
            "Standard",
            "What new fog games start with: FoW+, managed comms, new transactions, hidden AC discard, map connections, ghost hexes and the GM tools (GM turn map, Fog QoL 01)",
            true,
            EnumSet.of(
                    MANAGED_COMMS,
                    NEW_TRANSACTIONS,
                    HIDE_AC_DISCARD,
                    MAP_CONNECTIONS,
                    GHOST_HEXES,
                    GM_TURN_MAP,
                    FOG_QOL_01)),
    FOG_PLUS("plus", "Fog+", "FoW+ and the options it forces, nothing else", true, EnumSet.noneOf(FOWOption.class)),
    NORMAL(
            "normal",
            "Normal",
            "Classic fog: FoW+ off and nothing extra hidden",
            false,
            EnumSet.noneOf(FOWOption.class)),
    BEGINNER("beginner", "Beginner", "Like Normal, but supernovas are always visible", false, EnumSet.of(BRIGHT_NOVAS));

    private static final Set<FOWOption> LEFT_ALONE = EnumSet.of(
            GM_TURN_MAP,
            FOG_QOL_01,
            STATUS_SUMMARY,
            DISABLE_FRACTURE,
            FOG_SECRETS,
            OPTIONAL_PILLAGABLE_TG,
            CLASSIC_MAP_LAYOUT,
            FRACTURE_SEPARATE_MAP);

    private final String id;
    private final String title;
    private final String description;
    private final boolean fowPlus;
    private final Set<FOWOption> turnedOn;

    FogPreset(String id, String title, String description, boolean fowPlus, Set<FOWOption> turnedOn) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.fowPlus = fowPlus;
        this.turnedOn = turnedOn;
    }

    public Map<FOWOption, Boolean> optionValues() {
        Map<FOWOption, Boolean> values = new LinkedHashMap<>();
        for (FOWOption option : FOWOption.values()) {
            if (writes(option)) {
                values.put(option, valueOf(option));
            }
        }
        return values;
    }

    public static FogPreset fromId(String id) {
        if ("lite".equals(id)) {
            return BEGINNER;
        }
        for (FogPreset preset : values()) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("No fog preset '" + id + "'");
    }

    private boolean writes(FOWOption option) {
        return option.isVisible() && (!LEFT_ALONE.contains(option) || turnedOn.contains(option));
    }

    private boolean valueOf(FOWOption option) {
        if (fowPlus) {
            for (var forced : FOWPlusService.FORCED_FOWPLUS_OPTIONS) {
                if (forced.getLeft() == option) {
                    return forced.getRight();
                }
            }
        }
        return turnedOn.contains(option);
    }
}
