package ti4.image.draw;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import ti4.model.ModelInterface;
import ti4.website.model.WebsiteOverlay;

public final class WebsiteOverlays {

    private final List<WebsiteOverlay> overlays = new ArrayList<>();

    public void add(@Nullable ModelInterface dataModel, int x, int y, int width, int height) {
        if (dataModel == null) {
            return;
        }
        overlays.add(new WebsiteOverlay(dataModel, List.of(x, y, width, height)));
    }

    public void add(String title, String text, int x, int y, int width, int height) {
        overlays.add(new WebsiteOverlay(title, text, List.of(x, y, width, height)));
    }

    public int size() {
        return overlays.size();
    }

    public void truncateTo(int size) {
        overlays.subList(size, overlays.size()).clear();
    }

    public List<WebsiteOverlay> asList() {
        return overlays;
    }
}
