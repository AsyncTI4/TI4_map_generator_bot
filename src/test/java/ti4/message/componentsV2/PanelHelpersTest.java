package ti4.message.componentsV2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;

class PanelHelpersTest {

    @Test
    void confirmRowPutsConfirmFirstAndAGrayCancel() {
        ActionRow row = ComponentPanel.confirmRow(Buttons.red("go_1", "Yes, archive"), "cancel_1");

        List<Button> buttons = row.getButtons();
        assertThat(buttons).extracting(Button::getCustomId).containsExactly("go_1", "cancel_1");
        assertThat(buttons.get(1).getLabel()).isEqualTo("Cancel");
    }

    @Test
    void pageButtonsHideAtTheEdges() {
        assertThat(ComponentPanel.pageButtons(page -> "p_" + page, 0, 1, "◀", "▶"))
                .isEmpty();
        assertThat(ComponentPanel.pageButtons(page -> "p_" + page, 0, 3, "◀", "▶"))
                .extracting(Button::getCustomId)
                .containsExactly("p_1");
        assertThat(ComponentPanel.pageButtons(page -> "p_" + page, 1, 3, "◀", "▶"))
                .extracting(Button::getCustomId, Button::getLabel)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("p_0", "◀"),
                        org.assertj.core.groups.Tuple.tuple("p_2", "▶"));
        assertThat(ComponentPanel.pageButtons(page -> "p_" + page, 2, 3, "◀", "▶"))
                .extracting(Button::getCustomId)
                .containsExactly("p_1");
    }

    @Test
    void settingRowShowsNameValueAndHint() {
        Section row =
                ComponentPanel.settingRow("Game names", "pbd1234", "Shown in channels", Buttons.gray("c", "Change"));

        TextDisplay text = (TextDisplay) row.getContentComponents().getFirst();
        assertThat(text.getContent()).isEqualTo("**Game names:** pbd1234\n-# Shown in channels");
        assertThat(row.getAccessory().asButton().getCustomId()).isEqualTo("c");
    }

    @Test
    void settingRowWithoutHintHasNoSubtext() {
        Section row = ComponentPanel.settingRow("Map", "6p", null, Buttons.gray("c", "Change"));

        assertThat(((TextDisplay) row.getContentComponents().getFirst()).getContent())
                .isEqualTo("**Map:** 6p");
    }

    @Test
    void userPickerCarriesRangeAndSelection() {
        EntitySelectMenu menu = PanelPickers.users("players", "Choose", List.of("111", "222"), 1, 8);

        assertThat(menu.getEntityTypes()).containsExactly(SelectTarget.USER);
        assertThat(menu.getMinValues()).isEqualTo(1);
        assertThat(menu.getMaxValues()).isEqualTo(8);
        assertThat(menu.getPlaceholder()).isEqualTo("Choose");
        assertThat(menu.getDefaultValues())
                .extracting(EntitySelectMenu.DefaultValue::getId)
                .containsExactly("111", "222");
    }

    @Test
    void channelPickerLimitsChannelTypes() {
        EntitySelectMenu menu = PanelPickers.channels("ch", null, List.of(), 1, 1, ChannelType.TEXT);

        assertThat(menu.getEntityTypes()).containsExactly(SelectTarget.CHANNEL);
        assertThat(menu.getChannelTypes()).containsExactly(ChannelType.TEXT);
        assertThat(menu.getPlaceholder()).isNull();
    }

    @Test
    void formPickerThatAllowsNothingIsOptional() {
        // Discord rejects a form select with min 0 unless it is marked optional, so inForm does that.
        Label optional = PanelPickers.inForm("Avoid", PanelPickers.users("avoid", null, List.of(), 0, 5));
        Label required = PanelPickers.inForm("Players", PanelPickers.users("p", null, List.of(), 1, 5));

        assertThat(optional.getChild().asEntitySelectMenu().isRequired()).isFalse();
        assertThat(required.getChild().asEntitySelectMenu().isRequired()).isNull();
    }

    @Test
    void rolePickerUsesRoleTarget() {
        EntitySelectMenu menu = PanelPickers.roles("r", null, List.of("333"), 0, 1);

        assertThat(menu.getEntityTypes()).containsExactly(SelectTarget.ROLE);
        assertThat(menu.getDefaultValues())
                .extracting(EntitySelectMenu.DefaultValue::getId)
                .containsExactly("333");
    }
}
