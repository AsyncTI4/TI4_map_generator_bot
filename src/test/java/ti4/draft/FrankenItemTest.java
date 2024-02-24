package ti4.draft;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ti4.draft.items.*;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FrankenItemTest extends BaseTi4Test {
    @Test
    public void testAllCardsGenerateSuccessfully() {
        var factions = FrankenDraft.getAllFrankenLegalFactions();
        assertDoesNotThrow(() -> generateAllCards(factions));
    }

    @Test
    public void testAllCardsHaveValidShortNames() {
        var factions = FrankenDraft.getAllFrankenLegalFactions();
        var cards = generateAllCards(factions);
        for (var card: cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        }
    }

    @Test
    public void testAllCardsHaveValidLongNames() {
        var factions = FrankenDraft.getAllFrankenLegalFactions();
        var cards = generateAllCards(factions);
        for (var card: cards) {
            try {
                assert !card.getLongDescription().isEmpty() : card.getAlias();
            }
            catch (Exception e)
            {
                Assertions.fail(card.getAlias() + " threw an exception: " + e);
            }
        }
    }

    @Test
    public void testAllCardsHaveValidEmoji() {
        var factions = FrankenDraft.getAllFrankenLegalFactions();
        var cards = generateAllCards(factions);
        for (var card: cards) {
            assert !card.getItemEmoji().isEmpty() : card.getAlias();
        }
    }

    @Test
    public void errataFileSanityTest() {
        var factions = FrankenDraft.getAllFrankenLegalFactions();
        var cards = generateAllCards(factions);
        for (var card: cards) {
            // PoK
            assert(!card.getAlias().equals("ABILITY:mitosis"));
            assert(!card.getAlias().equals("ABILITY:hubris"));
            assert(!card.getAlias().equals("ABILITY:fragile"));
            assert(!card.getAlias().equals("STARTINGTECH:sardakk"));
            assert(!card.getAlias().equals("AGENT:mentakagent"));
            assert(!card.getAlias().equals("ABILITY:creuss_gate"));

            // DS
            assert(!card.getAlias().equals("ABILITY:probability_algorithms"));
            assert(!card.getAlias().equals("MECH:kjalengard_mech"));
            assert(!card.getAlias().equals("ABILITY:singularity_point"));
            assert(!card.getAlias().equals("HERO:vadenhero"));
            assert(!card.getAlias().equals("AGENT:mykomentoriagent"));
            assert(!card.getAlias().equals("ABILITY:stealth_insertion"));
        }
    }

    private List<DraftItem> generateAllCards(List<FactionModel> factions) {
        List<DraftItem> items = new ArrayList<>();
        items.addAll(AbilityDraftItem.buildAllDraftableItems(factions));
        items.addAll(TechDraftItem.buildAllDraftableItems(factions));
        items.addAll(AgentDraftItem.buildAllDraftableItems(factions));
        items.addAll(CommanderDraftItem.buildAllDraftableItems(factions));
        items.addAll(HeroDraftItem.buildAllDraftableItems(factions));
        items.addAll(HomeSystemDraftItem.buildAllDraftableItems(factions));
        items.addAll(PNDraftItem.buildAllDraftableItems(factions));
        items.addAll(CommoditiesDraftItem.buildAllDraftableItems(factions));
        items.addAll(StartingTechDraftItem.buildAllDraftableItems(factions));
        items.addAll(StartingFleetDraftItem.buildAllDraftableItems(factions));
        items.addAll(FlagshipDraftItem.buildAllDraftableItems(factions));
        items.addAll(MechDraftItem.buildAllDraftableItems(factions));

        return items;
    }
}
