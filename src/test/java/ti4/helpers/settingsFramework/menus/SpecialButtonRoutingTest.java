package ti4.helpers.settingsFramework.menus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.service.franken.FrankenDraftMode;
import ti4.testUtils.BaseTi4Test;

class SpecialButtonRoutingTest extends BaseTi4Test {

    // SettingsMenu.handleAction offers an action to the menu's settings before its special buttons, matching on
    // the setting id as a suffix. A special button whose action ends with a setting id (e.g.
    // "prioritizeDiscordantStars" vs the "DiscordantStars" range setting) never reaches its handler.
    @Test
    void frankenDrazSpecialButtonsAreNotCapturedBySettings() {
        FrankenSettings settings = new FrankenSettings(new Game(), null);
        settings.getDraftMode().setChosenKey(FrankenDraftMode.FRANKENDRAZ.toString());
        settings.getHomebrewSettings().getLostLegacies().setVal(true);
        settings.syncFrankendrazDsBrState("DraftMode");

        List<SettingsMenu> menus = allMenus(settings);
        assertFalse(menus.stream().allMatch(menu -> menu.specialButtons().isEmpty()));
        for (SettingsMenu menu : menus) {
            String prefix = SettingsMenu.menuAction + "_" + menu.navId() + "_";
            for (Button button : menu.specialButtons()) {
                String action = button.getCustomId().substring(prefix.length());
                SettingInterface captor = menu.settingForAction(action);
                assertNull(
                        captor,
                        () -> "Special action '" + action + "' in menu " + menu.navId() + " is captured by setting "
                                + captor.getId());
            }
        }
    }

    private static List<SettingsMenu> allMenus(SettingsMenu root) {
        List<SettingsMenu> menus = new ArrayList<>(List.of(root));
        for (SettingsMenu child : root.categories()) {
            menus.addAll(allMenus(child));
        }
        return menus;
    }
}
