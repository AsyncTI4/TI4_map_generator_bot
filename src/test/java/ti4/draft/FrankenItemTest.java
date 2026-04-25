package ti4.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Set;
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
        Set<String> unexpectedAliases = Set.of(
                // PoK
                "ABILITY:mitosis",
                "ABILITY:hubris",
                "ABILITY:fragile",
                "STARTINGTECH:sardakk",
                "AGENT:mentakagent",
                "ABILITY:creuss_gate",
                // DS
                "ABILITY:probability_algorithms",
                "MECH:kjalengard_mech",
                "ABILITY:singularity_point",
                "AGENT:mykomentoriagent",
                "ABILITY:stealth_insertion");
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            String alias = card.getAlias();
            assertFalse(unexpectedAliases.contains(alias), () -> "DraftItem was present but not expected: " + alias);
        }
    }
}
