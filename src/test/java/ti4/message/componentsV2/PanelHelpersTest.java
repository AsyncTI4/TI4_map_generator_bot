package ti4.message.componentsV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.DefaultValue;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;

class PanelHelpersTest {

    @Test
    void confirmRowPutsConfirmFirstAndAGrayCancel() {
        ActionRow row = ComponentPanel.confirmRow(Buttons.red("go_1", "Yes, archive"), "cancel_1");

        assertThat(row.getButtons())
                .extracting(Button::getCustomId, Button::getLabel)
                .containsExactly(tuple("go_1", "Yes, archive"), tuple("cancel_1", "Cancel"));
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
                .containsExactly(tuple("p_0", "◀"), tuple("p_2", "▶"));
        assertThat(ComponentPanel.pageButtons(page -> "p_" + page, 2, 3, "◀", "▶"))
                .extracting(Button::getCustomId)
                .containsExactly("p_1");
    }

    @Test
    void settingRowShowsNameValueAndAnOptionalHint() {
        Section withHint =
                ComponentPanel.settingRow("Game names", "pbd1234", "Shown in channels", Buttons.gray("c", "Change"));
        Section withoutHint = ComponentPanel.settingRow("Map", "6p", null, Buttons.gray("c", "Change"));

        assertThat(text(withHint)).isEqualTo("**Game names:** pbd1234\n-# Shown in channels");
        assertThat(withHint.getAccessory().asButton().getCustomId()).isEqualTo("c");
        assertThat(text(withoutHint)).isEqualTo("**Map:** 6p");
    }

    @Test
    void pickersCarryTargetRangePlaceholderAndSelection() {
        EntitySelectMenu users = PanelPickers.users("players", "Choose", List.of("111", "222"), 1, 8);
        EntitySelectMenu roles = PanelPickers.roles("r", null, List.of("333"), 0, 1);
        EntitySelectMenu channels = PanelPickers.channels("ch", null, List.of(), 1, 1, ChannelType.TEXT);

        assertThat(users.getEntityTypes()).containsExactly(SelectTarget.USER);
        assertThat(users.getMinValues()).isEqualTo(1);
        assertThat(users.getMaxValues()).isEqualTo(8);
        assertThat(users.getPlaceholder()).isEqualTo("Choose");
        assertThat(users.getDefaultValues()).extracting(DefaultValue::getId).containsExactly("111", "222");

        assertThat(roles.getEntityTypes()).containsExactly(SelectTarget.ROLE);
        assertThat(roles.getDefaultValues()).extracting(DefaultValue::getId).containsExactly("333");

        assertThat(channels.getEntityTypes()).containsExactly(SelectTarget.CHANNEL);
        assertThat(channels.getChannelTypes()).containsExactly(ChannelType.TEXT);
        assertThat(channels.getPlaceholder()).isNull();
    }

    @Test
    void formPickerThatAllowsNothingIsOptional() {
        // Discord rejects a form select with min 0 unless it is marked optional, so inForm does that.
        Label optional = PanelPickers.inForm("Avoid", PanelPickers.users("avoid", null, List.of(), 0, 5));
        Label required = PanelPickers.inForm("Players", PanelPickers.users("p", null, List.of(), 1, 5));

        assertThat(optional.getChild().asEntitySelectMenu().isRequired()).isFalse();
        assertThat(required.getChild().asEntitySelectMenu().isRequired()).isNull();
    }

    private static String text(Section row) {
        return ((TextDisplay) row.getContentComponents().getFirst()).getContent();
    }
}
