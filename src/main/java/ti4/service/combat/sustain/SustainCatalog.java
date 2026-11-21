package ti4.service.combat.sustain;

import java.util.List;

public interface SustainCatalog {

    List<SustainRule> rules();

    default List<CancelsTwoHitsRule> cancelsTwoHits() {
        return List.of();
    }
}
