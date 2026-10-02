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
                                    "argentbt",
                                    "augersbt",
                                    "axisbt",
                                    "bastionbt",
                                    "cymiaebt",
                                    "firmamentbt_y",
                                    "florzenbt",
                                    "gheminabt",
                                    "jolnarbt",
                                    "mortheusbt",
                                    "mykomentoribt",
                                    "uydaibt",
                                    "vaylerianbt")),
                    Map.entry(
                            Constants.ABILITY,
                            List.of(
                                    "cargo_raiders",
                                    "conspirators",
                                    "deep_mining",
                                    "foresight",
                                    "iconoclasm",
                                    "illusory_presence",
                                    "information_brokers",
                                    "moult",
                                    "munitions",
                                    "orbital_foundries",
                                    "rule_of_two",
                                    "starfall_gunnery",
                                    "zeal")),
                    Map.entry(
                            Constants.BAN_FLEET,
                            List.of("dihmohn", "edyn", "ghoti", "nekro", "qhet", "titans", "winnu")),
                    Map.entry(Constants.BAN_HS, List.of("ghemina", "gledge", "nokar", "saar", "thrones")),
                    Map.entry(
                            Constants.LEADER,
                            List.of(
                                    "argentagent",
                                    "cymiaecommander",
                                    "dihmohnagent",
                                    "edyncommander",
                                    "florzenagent",
                                    "gheminaagent",
                                    "hacancommander",
                                    "keleresagent",
                                    "keleresheroodlynn",
                                    "kjalengardcommander",
                                    "kolumecommander",
                                    "kolumehero",
                                    "kortaliagent",
                                    "kyrocommander",
                                    "l1z1xagent",
                                    "l1z1xcommander",
                                    "letnevagent",
                                    "mentakcommander",
                                    "mortheuscommander",
                                    "naalucommander",
                                    "nivynagent",
                                    "olradincommander",
                                    "solagent",
                                    "tnelisagent",
                                    "zealotscommander",
                                    "zeliancommander")),
                    Map.entry(
                            Constants.PROMISSORY_NOTE_ID,
                            List.of(
                                    "ambuscade",
                                    "dspnatok",
                                    "dspnchei",
                                    "dspnkhra",
                                    "dspnkolu",
                                    "dspnmirv",
                                    "dspnnoka",
                                    "dspnvayl",
                                    "dspnzeli",
                                    "ragh")),
                    Map.entry(
                            Constants.TECH,
                            List.of(
                                    "bio",
                                    "dsaxisb",
                                    "dsbentg",
                                    "dsbenty",
                                    "dscheir",
                                    "dsedyny",
                                    "dsflorg",
                                    "dsfreeg",
                                    "dskollb",
                                    "dskolly",
                                    "dskyrog",
                                    "dskyroy",
                                    "dsmirvr",
                                    "dsmortr",
                                    "dsmykog",
                                    "dsnivymf",
                                    "dsnivyy",
                                    "dsolrab",
                                    "dsolrar",
                                    "dsrhodb",
                                    "dsvaylr",
                                    "dsveldr",
                                    "dszelir",
                                    "gr",
                                    "htp",
                                    "ic",
                                    "is",
                                    "l4",
                                    "lgf",
                                    "parasite-firm_y",
                                    "scc",
                                    "so",
                                    "tp")),
                    Map.entry(
                            Constants.UNIT_ID,
                            List.of(
                                    "augers_flagship",
                                    "axis_mech",
                                    "cabal_mech",
                                    "cheiran_flagship",
                                    "edyn_flagship",
                                    "florzen_mech",
                                    "freesystems_flagship",
                                    "keleres_mech",
                                    "kollecc_flagship",
                                    "kolume_flagship",
                                    "l1z1x_mech",
                                    "mirveda_flagship",
                                    "mirveda_mech",
                                    "mortheus_mech",
                                    "mortheus_mech",
                                    "mykomentori_flagship",
                                    "mykomentori_mech",
                                    "nivyn_flagship",
                                    "nokar_flagship",
                                    "nokar_mech",
                                    "vaylerian_mech",
                                    "veldyr_flagship",
                                    "zealots_flagship",
                                    "zelian_flagship",
                                    "zelian_mech")),
                    Map.entry(Constants.TILE_NAME, List.of("102", "21", "22", "23")))),

    OP_Components(
            "OP Components",
            "Removes overpowered/toxic components for a more balanced draft",
            Map.ofEntries(
                    Map.entry(
                            Constants.LEADER,
                            List.of("edynhero", "lanefirhero", "mahactcommander_y", "naaluagent", "winnuhero")),
                    Map.entry(Constants.TECH, List.of("asn", "dsuydab", "dt2", "lw2", "qdn")),
                    Map.entry(
                            Constants.BREAKTHROUGH,
                            List.of("kyrobt", "lanefirbt", "letnevbt", "nomadbt", "solbt", "zealotsbt")),
                    Map.entry(Constants.UNIT_ID, List.of("mahact_mech_y")),
                    Map.entry(
                            Constants.ABILITY,
                            List.of(
                                    "classified_developments",
                                    "honor_bound",
                                    "pillage",
                                    "prescience",
                                    "sundered",
                                    "technological_singularity",
                                    "telepathic")),
                    Map.entry(Constants.TILE_NAME, List.of("115", "99"))));

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
        if (id == null) {
            return null;
        }

        for (FrankenBanList list : values()) {
            if (list.id.equalsIgnoreCase(id)) {
                return list;
            }
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
        if (searchString == null) {
            return false;
        }

        String lower = searchString.toLowerCase();

        if (id.toLowerCase().contains(lower)) {
            return true;
        }

        if (description.toLowerCase().contains(lower)) {
            return true;
        }

        for (Map.Entry<String, List<String>> e : bansByType.entrySet()) {
            if (e.getKey().toLowerCase().contains(lower)) {
                return true;
            }

            for (String v : e.getValue()) {
                if (v.toLowerCase().contains(lower)) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<String> getBansForType(String type) {
        return bansByType.getOrDefault(type, Collections.emptyList());
    }
}
