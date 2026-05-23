package ti4.helpers.settingsFramework.menus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.draft.FrankenDraft;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

class FrankenSettingsTest extends BaseTi4Test {

    @Test
    void routesOnlyDeterministicFrankenMenuIds() {
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_franken_startFranken")).isTrue();
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfN_franken.frankenComponents")).isTrue();
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_franken.frankenComponents_componentBanPick~add~ability~0"))
                .isTrue();

        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_milty_includeBanned_franken")).isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_frankenish_start")).isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent("prefix_jmfA_franken_start")).isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent(null)).isFalse();
    }

    @Test
    void componentValidationRequiresPlayableFactionComponent() {
        Game game = new Game();
        Set<String> legalAbilityIds = componentIds(game, FactionModel::getAbilities);
        String playableAbilityId = legalAbilityIds.stream().findFirst().orElseThrow();
        String nonPlayableAbilityId = Mapper.getAbilities().keySet().stream()
                .filter(id -> !legalAbilityIds.contains(id))
                .findFirst()
                .orElseThrow();

        assertThat(FrankenComponentBanSettings.isValidComponent(game, Constants.ABILITY, playableAbilityId))
                .isTrue();
        assertThat(FrankenComponentBanSettings.isValidComponent(game, Constants.ABILITY, nonPlayableAbilityId))
                .isFalse();
    }

    @Test
    void componentOptionsOnlyIncludePlayableFactionComponents() {
        Game game = new Game();
        Set<String> legalAbilityIds = componentIds(game, FactionModel::getAbilities);

        Set<String> optionIds = FrankenComponentBanSettings.componentOptions(game, Constants.ABILITY).stream()
                .map(Map.Entry::getKey)
                .map(FrankenSettingsTest::componentId)
                .collect(Collectors.toSet());

        assertThat(optionIds).isNotEmpty().allMatch(legalAbilityIds::contains);
    }

    @Test
    void factionDerivedComponentOptionsUsePlayableFactions() {
        Game game = new Game();
        Set<String> legalFactionIds = FrankenDraft.getAllFrankenLegalFactions(game).stream()
                .map(FactionModel::getAlias)
                .collect(Collectors.toSet());

        Set<String> optionIds = FrankenComponentBanSettings.componentOptions(game, Constants.BAN_COMMODITIES).stream()
                .map(Map.Entry::getKey)
                .map(FrankenSettingsTest::componentId)
                .collect(Collectors.toSet());

        assertThat(optionIds).containsExactlyInAnyOrderElementsOf(legalFactionIds);
    }

    private static Set<String> componentIds(Game game, Function<FactionModel, List<String>> ids) {
        return FrankenDraft.getAllFrankenLegalFactions(game).stream()
                .map(ids)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    private static String componentId(String componentKey) {
        return componentKey.split("\\|", 2)[1];
    }
}
