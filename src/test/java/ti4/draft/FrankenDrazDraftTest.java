package ti4.draft;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.draft.items.FactionDraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

class FrankenDrazDraftTest extends BaseTi4Test {

    @Test
    void generateDraftLoadsFrankenDraz() {
        Game game = new Game();

        assertInstanceOf(FrankenDrazDraft.class, BagDraft.generateDraft("frankendraz", game));
    }

    @Test
    void defaultFrankenDraftDoesNotUseFactionPackages() {
        Game game = new Game();
        FrankenDraft draft = new FrankenDraft(game);

        assertEquals(0, draft.getItemLimitForCategory(DraftCategory.FACTION));
        assertEquals(33, draft.getBagSize());
    }

    @Test
    void factionPackageMetadataAndAliasRoundTripWork() {
        Game game = new Game();
        DraftItem item = DraftItem.generate(DraftCategory.FACTION, "arborec");

        assertInstanceOf(FactionDraftItem.class, item);
        assertEquals(item, DraftItem.generateFromAlias(item.getAlias()));
        assertFalse(item.getTitle(game).isEmpty());
        assertFalse(item.getShortDescription().isEmpty());
        assertNotNull(item.getItemEmoji());
        assertFalse(((FactionDraftItem) item).getComponentList(game).isEmpty());
    }

    @Test
    void generateBagsUsesSixFactionPackagesAndMapSetupItems() {
        Game game = setupGame(6);
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);

        List<DraftBag> bags = assertDoesNotThrow(() -> draft.generateBags(game));

        assertNotNull(bags);
        assertEquals(6, bags.size());
        for (DraftBag bag : bags) {
            assertEquals(6, bag.getCategoryCount(DraftCategory.FACTION));
            assertEquals(3, bag.getCategoryCount(DraftCategory.BLUETILE));
            assertEquals(2, bag.getCategoryCount(DraftCategory.REDTILE));
            assertEquals(1, bag.getCategoryCount(DraftCategory.DRAFTORDER));
            assertEquals(12, bag.Contents.size());
        }
    }

    @Test
    void generateBagsAutoBansObsidianAndFirmament() {
        Game game = setupGame(6);
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);

        List<DraftBag> bags = assertDoesNotThrow(() -> draft.generateBags(game));

        assertNotNull(bags);
        assertFalse(bags.stream()
                .flatMap(bag -> bag.Contents.stream())
                .anyMatch(item -> item.getItemCategory() == DraftCategory.FACTION
                        && List.of("obsidian", "firmament").contains(item.getItemId())));
    }

    @Test
    void expansionRemovesPackagesAndAddsFactionComponents() {
        Game game = setupGame(1);
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);
        Player player = game.getRealPlayers().getFirst();

        player.getDraftHand().Contents.add(DraftItem.generate(DraftCategory.FACTION, "sol"));
        addMapSetupItems(player.getDraftHand());

        draft.expandFactionPackages(game);

        DraftBag hand = player.getDraftHand();
        assertEquals(0, hand.getCategoryCount(DraftCategory.FACTION));
        assertTrue(hand.getCategoryCount(DraftCategory.ABILITY) > 0);
        assertTrue(hand.getCategoryCount(DraftCategory.HOMESYSTEM) > 0);
        assertTrue(hand.getCategoryCount(DraftCategory.STARTINGFLEET) > 0);
        assertEquals(3, hand.getCategoryCount(DraftCategory.BLUETILE));
        assertEquals(2, hand.getCategoryCount(DraftCategory.REDTILE));
        assertEquals(1, hand.getCategoryCount(DraftCategory.DRAFTORDER));
        assertTrue(draft.isDraftStageComplete());
    }

    @Test
    void expansionRespectsExistingComponentBans() {
        Game game = setupGame(1);
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);
        Player player = game.getRealPlayers().getFirst();
        FactionModel sol = Mapper.getFaction("sol");
        String bannedAbility = sol.getAbilities().getFirst();
        game.setStoredValue("bannedAbilities", bannedAbility);

        player.getDraftHand().Contents.add(DraftItem.generate(DraftCategory.FACTION, "sol"));
        addMapSetupItems(player.getDraftHand());

        draft.expandFactionPackages(game);

        assertFalse(player.getDraftHand().Contents.stream()
                .anyMatch(item -> item.getItemCategory() == DraftCategory.ABILITY
                        && item.getItemId().equals(bannedAbility)));
    }

    @Test
    void mapSetupItemsAloneDoNotCompleteDraft() {
        Game game = setupGame(1);
        FrankenDrazDraft draft = new FrankenDrazDraft(game);
        game.setBagDraft(draft);
        Player player = game.getRealPlayers().getFirst();
        addMapSetupItems(player.getDraftHand());

        assertFalse(draft.isDraftStageComplete());
    }

    private static Game setupGame(int players) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("frankendraz-test");
        String[] colors = {"red", "blue", "green", "yellow", "purple", "orange"};
        for (int i = 1; i <= players; i++) {
            Player player = game.addPlayer("player" + i, "Player " + i);
            player.setFaction(game, "franken" + (i + 2));
            player.setColor(colors[i - 1]);
        }
        return game;
    }

    private static void addMapSetupItems(DraftBag hand) {
        hand.Contents.add(DraftItem.generate(DraftCategory.BLUETILE, "19"));
        hand.Contents.add(DraftItem.generate(DraftCategory.BLUETILE, "20"));
        hand.Contents.add(DraftItem.generate(DraftCategory.BLUETILE, "21"));
        hand.Contents.add(DraftItem.generate(DraftCategory.REDTILE, "39"));
        hand.Contents.add(DraftItem.generate(DraftCategory.REDTILE, "40"));
        hand.Contents.add(DraftItem.generate(DraftCategory.DRAFTORDER, "1"));
    }
}
