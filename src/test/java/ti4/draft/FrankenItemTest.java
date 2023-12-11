package ti4.draft;

import org.junit.jupiter.api.Test;
import ti4.draft.items.*;
import ti4.generator.Mapper;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FrankenItemTest {
    private List<FactionModel> getPoKFactions() {
        var factions = Mapper.getFactions();
        factions.removeIf(faction -> !faction.getSource().isPok());
        return factions;
    }

    private List<FactionModel> getDsFactions() {
        var factions = Mapper.getFactions();
        factions.removeIf(faction -> !faction.getSource().isDs());
        return factions;

    }

    @Test
    public void testAllPoKCardsGenerateSuccessfully() {
        var pok = getPoKFactions();
        assertDoesNotThrow(() -> generateAllCards(pok));
    }

    @Test
    public void testAllDSCardsGenerateSuccessfully() {
        var ds = getDsFactions();
        assertDoesNotThrow(() -> generateAllCards(ds));
    }

    @Test
    public void testAllPoKCardsHaveValidShortNames() {
        var pok = getPoKFactions();
        var cards = generateAllCards(pok);
        for (var card: cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        };
    }

    @Test
    public void testAllDSCardsHaveValidShortNames() {
        var ds = getDsFactions();
        var cards = generateAllCards(ds);
        for (var card: cards) {
            assert !card.getShortDescription().isEmpty() : card.getAlias();
        };
    }


    @Test
    public void testAllPoKCardsHaveValidLongNames() {
        var pok = getPoKFactions();
        var cards = generateAllCards(pok);
        for (var card: cards) {
            try {
                assert !card.getLongDescription().isEmpty() : card.getAlias();
            }
            catch (Exception e)
            {
                assert false : card.getAlias();
            }
        };
    }

    @Test
    public void testAllDSCardsHaveValidLongNames() {
        var ds = getDsFactions();
        var cards = generateAllCards(ds);
        for (var card: cards) {
            assert !card.getLongDescription().isEmpty() : card.getAlias();
        };
    }


    @Test
    public void testAllPoKCardsHaveValidEmoji() {
        var pok = getPoKFactions();
        var cards = generateAllCards(pok);
        for (var card: cards) {
            assert !card.getItemEmoji().isEmpty() : card.getAlias();
        };
    }

    @Test
    public void testAllDSCardsHaveValidEmoji() {
        var ds = getDsFactions();
        var cards = generateAllCards(ds);
        for (var card: cards) {
            assert !card.getItemEmoji().isEmpty() : card.getAlias();
        };
    }

    @Test
    public void errataFileSanityTest() {
        var pok = getPoKFactions();
        var cards = generateAllCards(pok);
        for (var card: cards) {
            assert(!card.getAlias().equals("ABILITY:mitosis"));
            assert(!card.getAlias().equals("ABILITY:hubris"));
            assert(!card.getAlias().equals("ABILITY:fragile"));
            assert(!card.getAlias().equals("STARTINGTECH:sardakk"));
            assert(!card.getAlias().equals("AGENT:mentakagent"));
            assert(!card.getAlias().equals("ABILITY:creuss_gate"));
        }

        var ds = getDsFactions();
        cards = generateAllCards(ds);
        for (var card: cards) {
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
