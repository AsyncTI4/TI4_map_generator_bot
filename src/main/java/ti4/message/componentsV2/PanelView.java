package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

public record PanelView(
        List<ContainerChildComponent> body,
        List<ActionRow> controls,
        boolean warning,
        boolean hideNavigation,
        int pageCount) {

    public PanelView {
        pageCount = Math.max(1, pageCount);
    }

    public static PanelView of(List<ContainerChildComponent> body, List<ActionRow> controls) {
        return new PanelView(body, controls, false, false, 1);
    }

    public static PanelView of(List<ContainerChildComponent> body, List<ActionRow> controls, boolean warning) {
        return new PanelView(body, controls, warning, false, 1);
    }

    public static PanelView subView(List<ContainerChildComponent> body, List<ActionRow> controls, boolean warning) {
        return new PanelView(body, controls, warning, true, 1);
    }

    public PanelView withWarning(boolean warning) {
        return new PanelView(body, controls, warning, hideNavigation, pageCount);
    }

    public PanelView withPages(int pageCount) {
        return new PanelView(body, controls, warning, hideNavigation, pageCount);
    }

    public PanelView asSubView() {
        return new PanelView(body, controls, warning, true, pageCount);
    }

    public PanelView withLead(ContainerChildComponent lead) {
        List<ContainerChildComponent> withLead = new ArrayList<>();
        withLead.add(lead);
        withLead.addAll(body);
        return new PanelView(withLead, controls, warning, hideNavigation, pageCount);
    }
}
