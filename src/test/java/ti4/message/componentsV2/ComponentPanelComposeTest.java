package ti4.message.componentsV2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;

class ComponentPanelComposeTest {

    private static final ActionRow CONTROLS = ActionRow.of(Buttons.gray("ctl", "Control"));
    private static final ActionRow NAV = ActionRow.of(Buttons.gray("nav", "Nav"));

    @Test
    void composesHeaderNoticeDividerBodyThenControlsThenNavigation() {
        PanelView view = PanelView.of(List.of(TextDisplay.of("body")), List.of(CONTROLS));

        List<MessageTopLevelComponent> components =
                ComponentPanel.compose("header", "line1\nline2", view, List.of(NAV));

        assertThat(components).hasSize(3);
        Container container = (Container) components.getFirst();
        List<? extends ContainerChildComponent> children = container.getComponents();
        assertThat(text(children.get(0))).isEqualTo("header");
        // Notices are quoted line by line so they read as a callout under the header.
        assertThat(text(children.get(1))).isEqualTo("> line1\n> line2");
        assertThat(children.get(2)).isInstanceOf(Separator.class);
        assertThat(text(children.get(3))).isEqualTo("body");
        assertThat(components.get(1)).isEqualTo(CONTROLS);
        assertThat(components.get(2)).isEqualTo(NAV);
    }

    @Test
    void blankNoticeAddsNoLine() {
        PanelView view = PanelView.of(List.of(TextDisplay.of("body")), List.of());

        Container container = (Container)
                ComponentPanel.compose("header", "  ", view, List.of()).getFirst();

        assertThat(container.getComponents()).hasSize(3);
        assertThat(container.getComponents().get(1)).isInstanceOf(Separator.class);
    }

    @Test
    void longNoticeIsCutToTheLimit() {
        String notice = "x".repeat(ComponentPanel.MAX_NOTICE_LENGTH * 2);
        PanelView view = PanelView.of(List.of(), List.of());

        Container container =
                (Container) ComponentPanel.compose("h", notice, view, List.of()).getFirst();

        // "> " prefix plus the abbreviated notice.
        assertThat(text(container.getComponents().get(1))).hasSize(ComponentPanel.MAX_NOTICE_LENGTH + 2);
    }

    @Test
    void accentFollowsWarning() {
        PanelView calm = PanelView.of(List.of(), List.of());

        assertThat(accent(ComponentPanel.compose("h", null, calm, List.of()))).isEqualTo(ComponentPanel.ACCENT);
        assertThat(accent(ComponentPanel.compose("h", null, calm.withWarning(true), List.of())))
                .isEqualTo(ComponentPanel.WARNING_ACCENT);
    }

    @Test
    void subViewHidesNavigationButKeepsControls() {
        PanelView view = PanelView.subView(List.of(), List.of(CONTROLS), false);

        List<MessageTopLevelComponent> components = ComponentPanel.compose("h", null, view, List.of(NAV));

        assertThat(components).hasSize(2).doesNotContain(NAV).contains(CONTROLS);
    }

    @Test
    void noticeIsDroppedWhenThePanelWouldNotFit() {
        // Fill the body close to the Components V2 character budget so only the notice tips it over.
        List<ContainerChildComponent> body = List.of(TextDisplay.of("y".repeat(3500)));
        PanelView view = PanelView.of(body, List.of());
        String notice = "z".repeat(800);

        List<MessageTopLevelComponent> components = ComponentPanel.compose("h", notice, view, List.of());

        assertThat(ComponentPanel.fits(components)).isTrue();
        Container container = (Container) components.getFirst();
        assertThat(container.getComponents().get(1)).isInstanceOf(Separator.class);
    }

    @Test
    void withLeadPutsTheLineFirstAndKeepsTheRest() {
        PanelView view = PanelView.of(List.of(TextDisplay.of("body")), List.of())
                .withPages(3)
                .withLead(TextDisplay.of("lead"));

        assertThat(view.body()).extracting(ComponentPanelComposeTest::text).containsExactly("lead", "body");
        assertThat(view.pageCount()).isEqualTo(3);
        assertThat(view.asSubView().hideNavigation()).isTrue();
    }

    @Test
    void panelIdsRoundTripTheTournamentFormats() {
        // These exact strings are on panels already posted in Discord, so the format must not change.
        assertThat(PanelIds.of("trnTab_", 42L, "GROUPS")).isEqualTo("trnTab_42_GROUPS");
        assertThat(PanelIds.of("trnPage_", 42L, "GROUPS", "x1")).isEqualTo("trnPage_42_GROUPS_x1");
        assertThat(PanelIds.of("trnRepost_", 42L)).isEqualTo("trnRepost_42");

        assertThat(PanelIds.key("trnTab_42_GROUPS", "trnTab_")).isEqualTo(42L);
        assertThat(PanelIds.arg("trnTab_42_GROUPS", "trnTab_")).isEqualTo("GROUPS");
        assertThat(PanelIds.arg("trnPage_42_GROUPS_x1", "trnPage_")).isEqualTo("GROUPS_x1");
        assertThat(PanelIds.key("trnRepost_42", "trnRepost_")).isEqualTo(42L);
        assertThat(PanelIds.arg("trnRepost_42", "trnRepost_")).isEmpty();
    }

    @Test
    void parseIntReturnsNullForNonNumbers() {
        assertThat(PanelForms.parseInt(" 7 ")).isEqualTo(7);
        assertThat(PanelForms.parseInt("seven")).isNull();
    }

    private static String text(ContainerChildComponent component) {
        return ((TextDisplay) component).getContent();
    }

    private static int accent(List<MessageTopLevelComponent> components) {
        return ((Container) components.getFirst()).getAccentColorRaw();
    }
}
