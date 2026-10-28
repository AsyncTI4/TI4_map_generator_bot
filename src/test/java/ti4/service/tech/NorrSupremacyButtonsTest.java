package ti4.service.tech;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// N'orr Supremacy lets the Sardakk research a unit upgrade, not any technology.
class NorrSupremacyButtonsTest extends BaseTi4Test {

    private Game game;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("norr-supremacy-test");
        sardakk = game.addPlayer("sardakkUser", "Sardakk Player");
        sardakk.setFaction(game, "sardakk");
        sardakk.setColor("red");
    }

    private static List<String> aliasesOf(List<Button> buttons) {
        return buttons.stream()
                .map(button -> StringUtils.substringAfter(button.getCustomId(), "getTech_"))
                .toList();
    }

    @Test
    void theResearchChoiceIsRoutedToTheUnitUpgradeList() {
        List<String> ids = ButtonHelperFactionSpecific.norrSupremacyButtons().stream()
                .map(Button::getCustomId)
                .toList();

        assertThat(ids)
                .contains("gain_CC_deleteThisMessage", "acquireAUnitUpgrade_deleteThisMessage", "deleteButtons")
                .doesNotContain("acquireATech_deleteThisMessage");
    }

    @Test
    void onlyUnitUpgradesYouMeetThePrerequisitesForAreOffered() {
        sardakk.addTech("ps");
        sardakk.addTech("da");

        List<String> offered = aliasesOf(ListTechService.unitUpgradeButtons(game, sardakk));

        assertThat(offered).contains("dd2");
        assertThat(offered)
                .allSatisfy(alias ->
                        assertThat(Mapper.getTech(alias).isUnitUpgrade()).isTrue());
        assertThat(offered).doesNotContain("inf2", "ps", "da");
    }

    @Test
    void nothingIsOfferedWithoutPrerequisites() {
        assertThat(ListTechService.unitUpgradeButtons(game, sardakk)).isEmpty();
    }
}
