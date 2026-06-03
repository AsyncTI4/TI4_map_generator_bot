package ti4.service.franken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import ti4.helpers.Constants;

public enum FrankenBanList {
    Weak_Components(
            "Weak Components",
            "Removes Weak/Boring Components for a more empowered draft",
            Map.ofEntries(
                    Map.entry(
                            Constants.BREAKTHROUGH,
                            List.of(
                                    "florzenbt",
                                    "axisbt",
                                    "cymiaebt",
                                    "mortheusbt",
                                    "mykomentoribt",
                                    "vaylerianbt",
                                    "argentbt",
                                    "firmamentbt",
                                    "bastionbt")),
                    Map.entry(
                            Constants.ABILITY,
                            List.of(
                                    "deep_mining",
                                    "orbital_foundries",
                                    "rule_of_two",
                                    "conspirators",
                                    "iconoclasm",
                                    "information_brokers",
                                    "munitions",
                                    "starfall_gunnery",
                                    "zeal",
                                    "illusory_presence",
                                    "foresight",
                                    "cargo_raiders")),
                    Map.entry(
                            Constants.BAN_FLEET,
                            List.of("titans", "edyn", "nekro", "winnu", "dihmohn", "ghoti", "qhet")),
                    Map.entry(Constants.BAN_HS, List.of("gledge", "nokar", "saar", "ghemina")),
                    Map.entry(
                            Constants.LEADER,
                            List.of(
                                    "argentagent",
                                    "keleresagent",
                                    "l1z1xagent",
                                    "dihmohnagent",
                                    "letnevagent",
                                    "tnelisagent",
                                    "gheminaagent",
                                    "kortaliagent",
                                    "florzenagent",
                                    "solagent",
                                    "nivynagent",
                                    "cymiaecommander",
                                    "zeliancommander",
                                    "mortheuscommander",
                                    "kolumecommander",
                                    "hacancommander",
                                    "l1z1xcommander",
                                    "edyncommander",
                                    "kyrocommander",
                                    "olradincommander",
                                    "mentakcommander",
                                    "naalucommander",
                                    "kjalengardcommander",
                                    "kolumehero",
                                    "keleresheroodlynn")),
                    Map.entry(
                            Constants.PROMISSORY_NOTE_ID,
                            List.of(
                                    "dspnkolu",
                                    "dspnmirv",
                                    "ambuscade",
                                    "ragh",
                                    "dspnchei",
                                    "dspnvayl",
                                    "dspnzeli",
                                    "dspnnoka",
                                    "dspnkhra")),
                    Map.entry(
                            Constants.TECH,
                            List.of(
                                    "dsvaylr",
                                    "dskolly",
                                    "dszelir",
                                    "dsmortr",
                                    "dsmykog",
                                    "dsfreeg",
                                    "dsolrar",
                                    "dsnivyy",
                                    "dsaxisb",
                                    "dsbentg",
                                    "dsmirvr",
                                    "dsrhodb",
                                    "dsnivymf",
                                    "dscheir",
                                    "dsolrab",
                                    "dsedyny",
                                    "dsveldr",
                                    "dskyroy",
                                    "dskollb",
                                    "dsflorg",
                                    "dsbenty",
                                    "dskyrog",
                                    "htp",
                                    "so",
                                    "ic",
                                    "bio",
                                    "l4",
                                    "lgf",
                                    "scc",
                                    "tp",
                                    "gr",
                                    "is")),
                    Map.entry(
                            Constants.UNIT_ID,
                            List.of(
                                    "kollecc_flagship",
                                    "cheiran_flagship",
                                    "mirveda_flagship",
                                    "veldyr_flagship",
                                    "kolume_flagship",
                                    "nivyn_flagship",
                                    "edyn_flagship",
                                    "nokar_flagship",
                                    "mykomentori_flagship",
                                    "zealots_flagship",
                                    "freesystems_flagship",
                                    "zelian_flagship",
                                    "augers_flagship",
                                    "vaylerian_mech",
                                    "cabal_mech",
                                    "l1z1x_mech",
                                    "mortheus_mech",
                                    "keleres_mech",
                                    "zelian_mech",
                                    "mirveda_mech",
                                    "florzen_mech",
                                    "mykomentori_mech",
                                    "nokar_mech",
                                    "mortheus_mech",
                                    "axis_mech")))),

    OP_Components(
            "OP Components",
            "Removes overpowered/toxic components for a more balanced draft",
            Map.ofEntries(
                    Map.entry(
                            Constants.LEADER,
                            List.of("winnuhero", "mahactcommander", "naaluagent", "edynhero", "lanefirhero")),
                    Map.entry(Constants.TECH, List.of("lw2", "dt2", "ffac2", "qdn", "asn", "dsuydab")),
                    Map.entry(
                            Constants.BREAKTHROUGH,
                            List.of("solbt", "letnevbt", "lanefirbt", "sardakkbt", "rhodunbt", "nomadbt", "kyrobt")),
                    Map.entry(Constants.UNIT_ID, List.of("mahact_mech")),
                    Map.entry(
                            Constants.ABILITY,
                            List.of(
                                    "telepathic",
                                    "technological_singularity",
                                    "pillage",
                                    "classified_developments",
                                    "honor_bound",
                                    "prescience")))),

    Blue_Reverie(
            "Blue Reverie",
            "Removes BR factions.",
            Map.ofEntries(Map.entry(
                    Constants.BAN_FACTION, List.of("atokera", "belkosea", "qhet", "pharadn", "toldar", "uydai"))));

    private final String id;

    @Getter
    private final String description;

    @Getter
    private final Map<String, List<String>> bansByType;

    FrankenBanList(String id, String description, Map<String, List<String>> bansByType) {
        this.id = id;
        this.description = description;
        this.bansByType = bansByType;
    }

    public String getName() {
        return id;
    }

    public List<String> getFlattenedBans() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : bansByType.entrySet()) {
            for (String v : e.getValue()) {
                out.add(e.getKey() + ": " + v);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static FrankenBanList fromString(String id) {
        if (id == null) return null;
        for (FrankenBanList list : values()) {
            if (list.id.equalsIgnoreCase(id)) return list;
        }
        return null;
    }

    public static List<String> getAvailableBanListNames() {
        List<String> names = new ArrayList<>();
        for (FrankenBanList list : values()) {
            names.add(list.id);
        }
        return names;
    }

    public static List<FrankenBanList> getAllBanLists() {
        return Arrays.asList(values());
    }

    @Override
    public String toString() {
        return id;
    }

    public String getAutoCompleteName() {
        return id + ": " + description;
    }

    public boolean search(String searchString) {
        if (searchString == null) return false;
        String lower = searchString.toLowerCase();
        if (id.toLowerCase().contains(lower)) return true;
        if (description.toLowerCase().contains(lower)) return true;
        for (Map.Entry<String, List<String>> e : bansByType.entrySet()) {
            if (e.getKey().toLowerCase().contains(lower)) return true;
            for (String v : e.getValue()) {
                if (v.toLowerCase().contains(lower)) return true;
            }
        }
        return false;
    }

    public List<String> getBansForType(String type) {
        return bansByType.getOrDefault(type, Collections.emptyList());
    }
}
