package ti4.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class FrankenItemTest extends BaseTi4Test {
    @Test
    void testAllCardsGenerateSuccessfully() {
        beforeAll();
        assertDoesNotThrow(DraftItem::generateAllDraftableCards);
    }

    @Test
    void testAllCardsHaveValidShortNames() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        }
    }

    @Test
    void testAllCardsHaveValidLongNames() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            try {
                assert !card.getShortDescription().isEmpty() : card.getAlias();
            } catch (Exception e) {
                Assertions.fail(card.getAlias() + " threw an exception: " + e);
            }
        }
    }

    @Test
    void testAllCardsHaveValidEmoji() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert card.getItemEmoji() != null : card.getAlias();
        }
    }

    @Test
    void errataFileSanityTest() {
        beforeAll();
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            // PoK
            Assertions.assertNotEquals("ABILITY:mitosis", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("ABILITY:hubris", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("ABILITY:fragile", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("STARTINGTECH:sardakk", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("AGENT:mentakagent", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("ABILITY:creuss_gate", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());

            // DS
            Assertions.assertNotEquals("ABILITY:probability_algorithms", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("MECH:kjalengard_mech", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("ABILITY:singularity_point", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("AGENT:mykomentoriagent", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
            Assertions.assertNotEquals("ABILITY:stealth_insertion", card.getAlias(),
                () -> "DraftItem was present but not expected: " + card.getAlias());
        }
    }
}
