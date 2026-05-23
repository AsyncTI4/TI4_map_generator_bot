package ti4.helpers.settingsFramework.menus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrankenSettingsTest {

    @Test
    void routesOnlyDeterministicFrankenMenuIds() {
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_franken_startFranken"))
                .isTrue();
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfN_franken.BannedFactions"))
                .isTrue();

        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_milty_includeBanned_franken"))
                .isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent("jmfA_frankenish_start"))
                .isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent("prefix_jmfA_franken_start"))
                .isFalse();
        assertThat(FrankenSettings.isFrankenMenuComponent(null)).isFalse();
    }
}
