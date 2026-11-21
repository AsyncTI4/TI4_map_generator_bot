package ti4.service.combat.sustain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ti4.service.combat.SustainCatalogs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every unit, tech, relic, leader, promissory note, ability, agenda or card whose text mentions SUSTAIN DAMAGE (or
 * cancelling 2 hits) is either handled by a sustain rule, or sits on the ignore list below with the reason it needs no
 * rule. A new card that talks about SUSTAIN DAMAGE fails this test until someone decides which.
 */
class SustainDataCoverageTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Pattern MENTIONS_SUSTAIN =
            Pattern.compile("sustain(ed|s|ing)?\\s+damage|cancel (2|two) hits", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKIPPED_FIELDS = Pattern.compile("image|url", Pattern.CASE_INSENSITIVE);
    private static final List<String> ID_FIELDS = List.of("id", "alias", "ID", "name");

    private static final String AFTER_SUSTAIN =
            "triggers after or when a unit uses SUSTAIN DAMAGE; belongs to the after-sustain hook PR";
    private static final String PRINTED =
            "restates a printed SUSTAIN DAMAGE that the unit model already carries (sustainDamage: true)";
    private static final String VALUE_INJECTION =
            "persistent grant or loss applied by UnitModelValueInjectionService, read as printed by every path";
    private static final String READS_OR_FORCES =
            "only checks whether a unit has SUSTAIN DAMAGE, or damages/repairs/destroys units by it; not a hit cancel";
    private static final String LAW_VARIANT =
            "variant of a law the bot only wires under its base id (schematics, articles_war)";
    private static final String KNOWN_GAP = "known gap, not implemented yet; see the sustain eligibility PR";
    private static final String BUDGET = "limits how many sustains may be used; belongs to the sustain budget PR";
    private static final String DEPRECATED = "deprecated card data";
    private static final String COVERED_ELSEWHERE = "handled by another rule id";

    private static final Map<String, String> IGNORED = ignored();

    private static Map<String, String> ignored() {
        Map<String, String> ignored = new HashMap<>();
        for (String id : List.of(
                "dh1",
                "dh2",
                "dh3",
                "dh4",
                "bsp_direct_hit",
                "bsp_direct_hit1",
                "bsp_direct_hit2",
                "bsp_direct_hit3",
                "bsp_direct_hit4",
                "miltymod_dh1",
                "miltymod_dh2",
                "miltymod_dh3",
                "miltymod_dh4",
                "pi_dh1",
                "pi_dh2",
                "pi_dh3",
                "pi_dh4",
                "dhll",
                "direct_hit_ds",
                "tf-spark1",
                "tf-spark2",
                "reflective",
                "ultimatetrickshotjmsc1",
                "ultimatetrickshotjmsc2",
                "letnevcommander",
                "bsp_letnevcommander",
                "bsp_letnev_alliance",
                "ponthousagent",
                "dsvadey",
                "tf-dsvadey",
                "beashendn",
                "ashen_dreadnought2",
                "bechoirr",
                "bechoirrlament",
                "bechoirrrequiem",
                "choir_mech",
                "choir_mechlament",
                "choir_mechrequiem",
                "sardakk_mech",
                "pi_sardakk_mech",
                "bsp_sardakk_mech",
                "thvanguardr",
                "vanguard_flagship",
                "heavy_rifle_hard_points",
                "empyrean_flagship",
                "sigma_empyrean_flagship_1",
                "sigma_empyrean_flagship_2",
                "dont_dtgs")) {
            ignored.put(id, AFTER_SUSTAIN);
        }
        for (String id : List.of(
                "ws",
                "dn2",
                "pws2",
                "ac2",
                "sdn2",
                "m2",
                "exo2",
                "se2",
                "ht2",
                "absol_ws",
                "absol_dn2",
                "absol_ac2",
                "absol_exo2",
                "absol_sdn2",
                "absol_pws2",
                "absol_ht2",
                "absol_se2",
                "absol_m2",
                "absol_tyrantslament",
                "bsp_sdn2",
                "bsp_ac2",
                "bsp_pws2",
                "dscheidn",
                "dsdihmdn",
                "dsnivymf",
                "dsrohdws",
                "dsvelddn",
                "baldrick_ws",
                "baldrick_pws2",
                "miltymod_dn2",
                "miltymod_ws",
                "miltymod_ac2",
                "miltymod_exo2",
                "miltymod_sdn2",
                "miltymod_pws2",
                "pi_sdn2",
                "pi_pws2",
                "pi_se2",
                "pi_ws",
                "pi_dn2",
                "thkryxosmf2",
                "baxanws",
                "bakalodn",
                "admins_mech",
                "opa_mech",
                "industriesbt",
                "cavalry")) {
            ignored.put(id, PRINTED);
        }
        for (String id : List.of(
                "choirbt",
                "carrier_customrig",
                "dreadnought_customrig",
                "radiant_grafting_scales",
                "atokerabt",
                "xytherisbt",
                "shielded_transports")) {
            ignored.put(id, VALUE_INJECTION);
        }
        for (String id : List.of(
                "raid_formation",
                "tf-raidformation",
                "morbid_harmony",
                "transformation_protocol",
                "emergency",
                "bsp_censure_retribution",
                "baldrick_galactic_tribute",
                "bsp_interfactional_unionization",
                "little_omega_shared_research",
                "little_omega_wormhole_research",
                "nakalim_flagship",
                "ralnel_flagship",
                "tf-linkship",
                "overpowering_defense",
                "target_powerful_ships",
                "nivynhero",
                "dshc_5_lizho",
                "mentak_monument",
                "argent_monument",
                "bapnxan",
                "precisiontargeting",
                "sigma_cow",
                "ggpncompany",
                "da",
                "absol_da",
                "bsp_da",
                "miltymod_da",
                "pi_dur",
                "dsnivyy")) {
            ignored.put(id, READS_OR_FORCES);
        }
        for (String id : List.of(
                "absol_schematics",
                "cryypter_schematics",
                "bsp_publicize_weapon",
                "byz_minister_of_commerce",
                "byz_publicize_weapons_schematics",
                "absol_articleswar",
                "bsp_articles_war",
                "byz_articles_of_war",
                "baldrick_articles_of_war",
                "little_omega_articles_war",
                "cryypter_articles_war")) {
            ignored.put(id, LAW_VARIANT);
        }
        // The seed gaps from the sustain divergence audit, plus the others the scan found.
        for (String id : List.of(
                "pi_mentak_flagship",
                "pi_mentak_mech",
                "bsp_mentak_mech",
                "diaspora_mech",
                "absol_xxcha_mech",
                "absol_nes",
                "pi_nomad_mech",
                "qhet_flagship",
                "dspnqhet",
                "kolume_flagship",
                "electromagneticflux30k",
                "ggpnclade",
                "sarcosahero",
                "luopar",
                "riftborn_flagship",
                "keleresplusherodihmohn",
                "retrofit",
                "illusory_duplication_ds",
                "qhetbt",
                "xanbt",
                "bsp_gls",
                "malfunction")) {
            ignored.put(id, KNOWN_GAP);
        }
        for (String id : List.of("sigma_mentak_flagship_1", "sigma_hamstring")) {
            ignored.put(id, BUDGET);
        }
        for (String id : List.of(
                "illusory_duplication",
                "sensor_jam",
                "boarding_torpedoes",
                "direct_hit1_acd2",
                "direct_hit2_acd2",
                "direct_hit3_acd2",
                "direct_hit4_acd2")) {
            ignored.put(id, DEPRECATED);
        }
        // tf-nes: Player.hasTech("nes") also matches the Twilight's Fall copy. thkryxosfs2 upgrades to
        // kryxos_flagship3, and naazbt flips the mech to naaz_voltron; those unit ids carry the rule.
        for (String id : List.of("tf-nes", "thkryxosfs2", "naazbt")) {
            ignored.put(id, COVERED_ELSEWHERE);
        }
        return ignored;
    }

    @Test
    void everyDataEntryThatMentionsSustainDamageIsCoveredOrIgnored() throws IOException {
        Set<String> uncovered = new TreeSet<>(entriesMentioningSustain());
        uncovered.removeAll(ruleIds());
        uncovered.removeAll(IGNORED.keySet());

        assertThat(uncovered)
                .as("give these a sustain rule, or add them to the ignore list with a reason")
                .isEmpty();
    }

    @Test
    void theIgnoreListHoldsNoStaleOrRuleCoveredIds() throws IOException {
        Set<String> mentioning = entriesMentioningSustain();
        Set<String> stale = new TreeSet<>(IGNORED.keySet());
        stale.removeAll(mentioning);
        Set<String> doubleBooked = new TreeSet<>(IGNORED.keySet());
        doubleBooked.retainAll(ruleIds());

        assertThat(stale)
                .as("ignored ids that no longer mention SUSTAIN DAMAGE")
                .isEmpty();
        assertThat(doubleBooked).as("ignored ids that a rule already covers").isEmpty();
    }

    private static Set<String> ruleIds() {
        Set<String> ids = new HashSet<>();
        SustainCatalogs.rules().forEach(rule -> ids.add(rule.id()));
        SustainCatalogs.cancelsTwoHits().forEach(rule -> ids.add(rule.id()));
        return ids;
    }

    private static Set<String> entriesMentioningSustain() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.walk(DATA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> !DATA.relativize(path).startsWith("testbed"))
                    .toList()) {
                JsonNode root = mapper.readTree(file.toFile());
                for (JsonNode entry : root.values()) {
                    if (entry.isObject() && mentionsSustain(entry)) {
                        ids.add(idOf(entry, DATA.relativize(file)));
                    }
                }
            }
        }
        return ids;
    }

    private static boolean mentionsSustain(JsonNode entry) {
        return entry.properties().stream()
                .filter(field -> !SKIPPED_FIELDS.matcher(field.getKey()).find())
                .map(Map.Entry::getValue)
                .anyMatch(value -> value.isString()
                        && MENTIONS_SUSTAIN.matcher(value.asString()).find());
    }

    private static String idOf(JsonNode entry, Path file) {
        return ID_FIELDS.stream()
                .map(entry::get)
                .filter(value -> value != null && value.isString())
                .map(JsonNode::asString)
                .findFirst()
                .orElse("entry without an id in " + file);
    }
}
