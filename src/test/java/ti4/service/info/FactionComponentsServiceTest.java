package ti4.service.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class FactionComponentsServiceTest extends BaseTi4Test {

    // Placeholder factions (Twilight's Fall, Franken, TK Nova) have no home system, starting tech or fleet,
    // so every card type must cope with missing data instead of throwing.
    @Test
    void buildsComponentCardsForEveryFaction() {
        List<String> failures = new ArrayList<>();
        for (String faction : Mapper.getFactionIDs()) {
            try {
                FactionComponentsService.cards(faction, true);
            } catch (RuntimeException e) {
                failures.add(faction + ": " + e);
            }
        }
        assertThat(failures).isEmpty();
    }

    // An extra anchored to a card the faction doesn't show would silently vanish from the dump.
    @Test
    void everyExtraIsAnchoredToAShownCard() {
        List<String> failures = new ArrayList<>();
        for (FactionComponentExtras extras : FactionComponentExtrasCatalog.EXTRAS) {
            Set<String> unplaced = FactionComponentsService.unplacedExtraAnchors(extras.faction());
            if (!unplaced.isEmpty()) {
                failures.add(extras.faction() + ": " + unplaced);
            }
        }
        assertThat(failures).isEmpty();
    }
}
