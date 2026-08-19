package ti4.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class FrankenItemTest extends BaseTi4Test {
    @Test
    void frankenDrazUsesConfiguredFactionLimit() {
        Game game = new Game();
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);

        Assertions.assertEquals(6, draft.getItemLimitForCategory(DraftCategory.FACTION));

        game.setStoredValue("frankenLimit" + DraftCategory.FACTION, "4");
        Assertions.assertEquals(4, draft.getItemLimitForCategory(DraftCategory.FACTION));
        Assertions.assertEquals(4, FrankenDraft.getItemLimitForCategory(DraftCategory.FACTION, game));
        Assertions.assertEquals(10, draft.getBagSize());
        Assertions.assertEquals(10, game.getFrankenBagSize());

        game.setStoredValue("frankenLimit" + DraftCategory.FACTION, "8");
        Assertions.assertEquals(8, draft.getItemLimitForCategory(DraftCategory.FACTION));
        Assertions.assertEquals(8, FrankenDraft.getItemLimitForCategory(DraftCategory.FACTION, game));
        Assertions.assertEquals(14, draft.getBagSize());
        Assertions.assertEquals(14, game.getFrankenBagSize());
    }

    @Test
    void testAllCardsGenerateSuccessfully() {
        assertDoesNotThrow(DraftItem::generateAllDraftableCards);
    }

    @Test
    void testAllCardsHaveValidShortNames() {
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        }
    }

    @Test
    void testAllCardsHaveValidLongNames() {
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
        List<DraftItem> cards = DraftItem.generateAllDraftableCards();
        for (DraftItem card : cards) {
            assert card.getItemEmoji() != null : card.getAlias();
        }
    }

    @Test
    void errataFileSanityTest() {
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
