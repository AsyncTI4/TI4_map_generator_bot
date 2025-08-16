package ti4.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

public class FrankenItemTest extends BaseTi4Test {
    @Test
    public void testAllCardsGenerateSuccessfully() {
        beforeAll();
        assertDoesNotThrow(DraftItem::generateAllDraftableCards);
    }

    @Test
    public void testAllCardsHaveValidShortNames() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        }
    }

    @Test
    public void testAllCardsHaveValidLongNames() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            try {
                assert !card.getLongDescription().isEmpty() : card.getAlias();
            } catch (Exception e) {
                Assertions.fail(card.getAlias() + " threw an exception: " + e);
            }
        }
    }

    @Test
    public void testAllCardsHaveValidEmoji() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert card.getItemEmoji() != null : card.getAlias();
        }
    }

    @Test
    public void errataFileSanityTest() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            // PoK
            assert (!"ABILITY:mitosis".equals(card.getAlias()));
            assert (!"ABILITY:hubris".equals(card.getAlias()));
            assert (!"ABILITY:fragile".equals(card.getAlias()));
            assert (!"STARTINGTECH:sardakk".equals(card.getAlias()));
            assert (!"AGENT:mentakagent".equals(card.getAlias()));
            assert (!"ABILITY:creuss_gate".equals(card.getAlias()));

            // DS
            assert (!"ABILITY:probability_algorithms".equals(card.getAlias()));
            assert (!"MECH:kjalengard_mech".equals(card.getAlias()));
            assert (!"ABILITY:singularity_point".equals(card.getAlias()));
            assert (!"AGENT:mykomentoriagent".equals(card.getAlias()));
            assert (!"ABILITY:stealth_insertion".equals(card.getAlias()));
        }
    }
}
