package ti4.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.ResourceHelper;
import ti4.image.Mapper;
import ti4.model.enums.AutomationStatus;
import ti4.testUtils.BaseTi4Test;

class BorderAnomalyModelTest extends BaseTi4Test {

    // Mapper only logs JSON import failures, so the exact id set is asserted here.
    private static final Set<String> EXPECTED_IDS = Set.of(
            "asteroid",
            "gravity_wave",
            "nebula",
            "minefield",
            "spatial_tear",
            "void_tether",
            "core_border",
            "rim_border",
            "yellow",
            "redorange");

    // Every spelling the old BorderAnomalyType enum accepted (enum name, toSearchString, display name),
    // mapped to the id it must resolve to now. Saves and old map JSON exports still contain these.
    private static final Map<String, List<String>> LEGACY_SPELLINGS = Map.of(
            "asteroid", List.of("ASTEROID", "asteroid", "Asteroid Field"),
            "gravity_wave", List.of("GRAVITY_WAVE", "gravitywave", "Gravity Wave"),
            "nebula", List.of("NEBULA", "nebula", "Nebula"),
            "minefield", List.of("MINEFIELD", "minefield", "Minefield"),
            "spatial_tear", List.of("SPATIAL_TEAR", "spatialtear", "Spatial Tear"),
            "void_tether", List.of("VOID_TETHER", "voidtether", "Void Tether"),
            "core_border", List.of("CORE_BORDER", "coreborder", "Core border"),
            "rim_border", List.of("RIM_BORDER", "rimborder", "Rim border"),
            "yellow", List.of("YELLOW", "yellow", "Yellow"),
            "redorange", List.of("REDORANGE", "redorange", "RedOrange"));

    @Test
    void loadsExactlyTheExpectedBorderAnomalies() {
        Set<String> ids = Mapper.getBorderAnomalies().stream()
                .map(BorderAnomalyModel::getId)
                .collect(Collectors.toSet());
        assertThat(ids).isEqualTo(EXPECTED_IDS);
    }

    @Test
    void everyBorderAnomalyIsValidWithLowerCaseIdAndExistingImage() {
        for (BorderAnomalyModel model : Mapper.getBorderAnomalies()) {
            assertThat(model.isValid()).as(model.getId() + " is valid").isTrue();
            assertThat(model.getId()).isEqualTo(model.getId().toLowerCase());
            assertThat(ResourceHelper.getResourceFromFolder("borders/", model.getImagePath()))
                    .as("image for " + model.getId())
                    .isNotNull();
        }
    }

    @Test
    void adjacencyRulesMatchTheFormerHardcodedBehaviour() {
        // Jackson ignores misspelt keys, so a typo in rules.adjacency would silently drop a rule.
        for (BorderAnomalyModel model : Mapper.getBorderAnomalies()) {
            boolean expectedIn = Set.of("spatial_tear", "gravity_wave").contains(model.getId());
            boolean expectedOut = "spatial_tear".equals(model.getId());
            assertThat(model.blocksAdjacencyIn())
                    .as(model.getId() + " blocksIn")
                    .isEqualTo(expectedIn);
            assertThat(model.blocksAdjacencyOut())
                    .as(model.getId() + " blocksOut")
                    .isEqualTo(expectedOut);
        }
    }

    @Test
    void entriesWithoutAutomationDeclareNoRules() {
        for (BorderAnomalyModel model : Mapper.getBorderAnomalies()) {
            if (model.getAutomation() == AutomationStatus.MANUAL
                    || model.getAutomation() == AutomationStatus.NO_RULES) {
                assertThat(model.declaresAdjacencyRules())
                        .as(model.getId() + " is " + model.getAutomation() + " but declares rules")
                        .isFalse();
            }
        }
    }

    @Test
    void legacySpellingsResolveToTheNewIds() {
        LEGACY_SPELLINGS.forEach((expectedId, spellings) -> {
            for (String spelling : spellings) {
                BorderAnomalyModel model = Mapper.resolveBorderAnomaly(spelling);
                assertThat(model).as("resolving " + spelling).isNotNull();
                assertThat(model.getId()).as("resolving " + spelling).isEqualTo(expectedId);
            }
        });
    }

    @Test
    void retiredArrowTypeNoLongerResolves() {
        assertThat(Mapper.resolveBorderAnomaly("ARROW")).isNull();
        assertThat(Mapper.resolveBorderAnomaly("arrow")).isNull();
    }

    @Test
    void everyIdReferencedInCodeExistsInData() {
        for (String id : BorderAnomalyIds.REFERENCED_IN_CODE) {
            assertThat(Mapper.getBorderAnomaly(id)).as(id).isNotNull();
        }
    }
}
