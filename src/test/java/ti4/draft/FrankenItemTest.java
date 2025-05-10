package ti4.draft;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
            assert (!card.getAlias().equals("ABILITY:mitosis"));
            assert (!card.getAlias().equals("ABILITY:hubris"));
            assert (!card.getAlias().equals("ABILITY:fragile"));
            assert (!card.getAlias().equals("STARTINGTECH:sardakk"));
            assert (!card.getAlias().equals("AGENT:mentakagent"));
            assert (!card.getAlias().equals("ABILITY:creuss_gate"));

            // DS
            assert (!card.getAlias().equals("ABILITY:probability_algorithms"));
            assert (!card.getAlias().equals("MECH:kjalengard_mech"));
            assert (!card.getAlias().equals("ABILITY:singularity_point"));
            assert (!card.getAlias().equals("AGENT:mykomentoriagent"));
            assert (!card.getAlias().equals("ABILITY:stealth_insertion"));
        }
    }
}
