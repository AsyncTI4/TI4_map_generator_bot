package ti4.service.franken;

import java.util.*;
import java.util.stream.Collectors;
import ti4.helpers.Constants;

public enum FrankenBanList {
    CARTER_CUT(
        "carter_cut",
        "Removes Weak & Unbalanced Components for a more balanced draft",
        Map.ofEntries(
            Map.entry(Constants.ABILITY, List.of(
                "hired_guns",
                "deep_mining",
                "deep_mining",
                "orbital_foundries",
                "rule_of_two",
                "conspirators",
                "iconoclasm",
                "information_brokers",
                "munitions",
                "starfall_gunnery",
                "plague_reservoir",
                "zeal",
                "illusory_presence",
                "foresight",
                "cargo_raiders",
                "pillage",
                "technological_singularity",
                "classified_developments",
                "data_recovery",
                "honor_bound",
                "prescience"
            )),
            Map.entry(Constants.BAN_FLEET, List.of(
                "titans",
                "edyn",
                "nekro",
                "winnu",
                "dihmohn",
                "ghoti",
                "qhet"
            )),
            Map.entry(Constants.BAN_HS, List.of(
                "gledge",
                "nokar",
                "saar",
                "ghemina"
            )),
            Map.entry(Constants.LEADER, List.of(
                "argentagent",
                "l1z1xagent",
                "dihmohnagent",
                "letnevagent",
                "tnelisagent",
                "gheminaagent",
                "kortaliagent",
                "florzenagent",
                "solagent",
                "nivynagent",
                "ghostcommander",
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
                "naaluhero",
                "kolumehero",
                "keleresheroodlynn",
                "winnuhero"
            )),
            Map.entry(Constants.PROMISSORY_NOTE_ID, List.of(
                "dspnkolu",
                "dspnmirv",
                "ambuscade",
                "ragh",
                "dspnchei",
                "dspnvayl",
                "dspnzeli",
                "dspnnoka",
                "dspnkhra"
            )),
            Map.entry(Constants.TECH, List.of(
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
                "is",
                "dsuydab"
            )),
            Map.entry(Constants.UNIT_ID, List.of(
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
                "rohdhna_mech",
                "cabal_mech",
                "nivyn_mech",
                "l1z1x_mech",
                "mortheus_mech",
                "keleres_mech",
                "zelian_mech",
                "mirveda_mech"
            ))
        )
    ),

    FOUR_PLAYER_BANS(
        "4p_bans",
        "Common Removes for 4P Franken games to balance out gameplay",
        Map.ofEntries(
            Map.entry(Constants.LEADER, List.of(
                "winnuhero",
                "mahactcommander",
                "xxchahero",
                "naaluagent"
            )),
            Map.entry(Constants.TECH, List.of(
                "lw2",
                "dt2",
                "ffac2",
                "qdn",
                "asn"
            )),
            Map.entry(Constants.UNIT_ID, List.of(
                "mahact_mech"
            )),
            Map.entry(Constants.ABILITY, List.of(
                "telepathic",
                "technological_singularity"
            ))
        )
    );

    private final String id;
    private final String description;
    private final Map<String, List<String>> bansByType;

    FrankenBanList(String id, String description, Map<String, List<String>> bansByType) {
        this.id = id;
        this.description = description;
        this.bansByType = bansByType.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    public String getName() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, List<String>> getBansByType() {
        return bansByType;
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
