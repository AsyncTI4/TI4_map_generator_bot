package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

// Past 25 buttons the row builder drops the "2 units" variants to make the message fit. It used to drop every button
// whose id contained "_2", which in a ring-two system (tiles 201-212) is every button naming the system, Done
// Landing Troops included: the landing message was left with no way to finish the tactical action.
class ButtonActionRowsTest extends BaseTi4Test {

    private static final String DONE_LANDING = "FFCC_hacan_doneLanding_205";

    private static List<String> idsIn(List<MessageTopLevelComponent> rows) {
        List<String> ids = new ArrayList<>();
        for (MessageTopLevelComponent row : rows) {
            ((ActionRow) row).getButtons().forEach(button -> ids.add(button.getCustomId()));
        }
        return ids;
    }

    private static Button button(String id) {
        return Button.of(ButtonStyle.SECONDARY, id, "Option");
    }

    private static List<Button> landingMessageInRingTwo() {
        List<Button> buttons = new ArrayList<>();
        for (String planet : List.of("horizon", "elnath", "luthieniv")) {
            for (String unit : List.of("gf", "mf")) {
                buttons.add(button("FFCC_hacan_landUnits_205_1" + unit + "_" + planet + "_copper"));
                buttons.add(button("FFCC_hacan_landUnits_205_2" + unit + "_" + planet + "_copper"));
                buttons.add(button("FFCC_hacan_spaceUnits_205_1" + unit + "_" + planet + "_copper"));
                buttons.add(button("FFCC_hacan_spaceUnits_205_2" + unit + "_" + planet + "_copper"));
            }
            buttons.add(button("sardakkcommander_infantry_" + planet + "_garbozia"));
            buttons.add(button("sardakkcommander_mech_" + planet + "_tinnes"));
        }
        buttons.add(button(DONE_LANDING));
        return buttons;
    }

    @Test
    void keepsTheDoneButtonAndOneUnitButtonsOfARingTwoSystemWhenTrimming() {
        List<Button> buttons = landingMessageInRingTwo();
        assertThat(buttons.size()).isGreaterThan(25);

        List<String> kept = idsIn(ButtonHelper.turnButtonListIntoActionRowList(buttons));

        assertThat(kept)
                .contains(DONE_LANDING, "FFCC_hacan_landUnits_205_1gf_luthieniv_copper")
                .contains("sardakkcommander_mech_elnath_tinnes")
                .doesNotContain("FFCC_hacan_landUnits_205_2gf_luthieniv_copper")
                .doesNotContain("FFCC_hacan_spaceUnits_205_2mf_horizon_copper");
        assertThat(kept).hasSizeLessThanOrEqualTo(25);
    }

    @Test
    void keepsEveryButtonWhenTheyFit() {
        List<Button> buttons = landingMessageInRingTwo().subList(0, 25);

        assertThat(idsIn(ButtonHelper.turnButtonListIntoActionRowList(buttons)))
                .hasSize(25)
                .contains("FFCC_hacan_landUnits_205_2gf_horizon_copper");
    }

    @Test
    void recognisesOnlyTheTwoUnitVariants() {
        assertThat(ButtonHelper.isCountTwoVariant("FFCC_hacan_landUnits_207_2gf_luthieniv_copper"))
                .isTrue();
        assertThat(ButtonHelper.isCountTwoVariant("FFCC_hacan_unitTacticalMove_307_2_gf_arretze_copper"))
                .isTrue();
        assertThat(ButtonHelper.isCountTwoVariant("FFCC_hacan_landUnits_207_1gf_luthieniv_copper"))
                .isFalse();
        assertThat(ButtonHelper.isCountTwoVariant("FFCC_hacan_doneLanding_207")).isFalse();
        assertThat(ButtonHelper.isCountTwoVariant("ringTile_205")).isFalse();
        assertThat(ButtonHelper.isCountTwoVariant(null)).isFalse();
    }
}
