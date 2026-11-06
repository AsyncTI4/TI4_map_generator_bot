package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.helpers.UnusedCommanderHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.objectives.DrawSecretService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.testUtils.BaseTi4Test;

class Acd2CardUpdatesTest extends BaseTi4Test {
    private Game game;
    private Player player;
    private Tile tile;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("acd2-updates");
        game.setExploreDeck(new java.util.ArrayList<>());
        game.addPlayer("actor", "Actor");
        player = game.getPlayer("actor");
        player.setFaction("sol");
        player.setColor("blue");
        player.getUnitsOwned()
                .addAll(List.of(
                        "fighter",
                        "destroyer",
                        "cruiser",
                        "carrier",
                        "dreadnought",
                        "infantry",
                        "pds",
                        "spacedock",
                        "sol_mech",
                        "sol_flagship"));
        game.addPlayer("opponent", "Opponent");
        Player opponent = game.getPlayer("opponent");
        opponent.setFaction("hacan");
        opponent.setColor("red");
        opponent.getUnitsOwned()
                .addAll(List.of("fighter", "destroyer", "cruiser", "carrier", "dreadnought", "hacan_flagship"));
        tile = new Tile("37", "101");
        game.setTile(tile);
        game.setActiveSystem("101");
        event = mock(ButtonInteractionEvent.class);
    }

    @Test
    void fracturedRealityOffersGroundForcesAndRespectsTheRoll() {
        List<UnitModel> units = Acd2UnitHelper.getUnitsWithCostAtMost(player, tile, 1);
        assertThat(units)
                .extracting(UnitModel::getUnitType)
                .contains(UnitType.Fighter, UnitType.Infantry, UnitType.Destroyer)
                .doesNotContain(UnitType.Mech, UnitType.Carrier, UnitType.Pds, UnitType.Spacedock);
        assertThat(Acd2UnitHelper.getUnitsWithCostAtMost(player, tile, 2))
                .extracting(UnitModel::getUnitType)
                .contains(UnitType.Mech, UnitType.Cruiser);
    }

    @Test
    void boardingPartyOffersOnlyShipsWithCostAtMostFour() {
        assertThat(BoardingPartyAcd2ButtonHandler.getEligibleShips(player, game, tile))
                .extracting(UnitModel::getUnitType)
                .contains(
                        UnitType.Fighter, UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought)
                .doesNotContain(UnitType.Infantry, UnitType.Mech, UnitType.Flagship, UnitType.Pds);
    }

    @Test
    void boardingPartyUsesTheDestroyedShipsCostRatherThanTheReplacementShipsCost() {
        Player actor = spy(player);
        UnitModel expensiveCarrier = new UnitModel();
        expensiveCarrier.setBaseType("carrier");
        expensiveCarrier.setAsyncId("cv");
        expensiveCarrier.setIsShip(true);
        expensiveCarrier.setCost(5);
        doReturn(expensiveCarrier).when(actor).getPriorityUnitByAsyncID("cv", tile.getSpaceUnitHolder());
        assertThat(BoardingPartyAcd2ButtonHandler.getEligibleShips(actor, game, tile))
                .contains(expensiveCarrier);
    }

    @Test
    void boardingPartyPlacesExactlyOneFighter() {
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var units = mockStatic(AddUnitService.class)) {
            BoardingPartyAcd2ButtonHandler.resolveBoardingPartyPlace(player, game, event, "boardingPartyPlace_101_ff");
            units.verify(() -> AddUnitService.addUnits(event, tile, game, player.getColor(), "1 ff"));
        }
    }

    @Test
    void fracturedRealityRollsOnceAndConsumesTheReplacementChoice() {
        tile.addUnit("space", Units.getUnitKey(UnitType.Cruiser, player.getColorID()), 1);
        DiceHelper.Die die = DiceHelper.spoof(10, 1);
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var dice = mockStatic(DiceHelper.class);
                var units = mockStatic(AddUnitService.class)) {
            dice.when(() -> DiceHelper.rollDice(10, 1)).thenReturn(List.of(die));
            FracturedRealityAcd2ButtonHandler.resolveFracturedRealityShip(
                    player, game, event, "fracturedRealityShip_101_Cruiser");
            assertThat(tile.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, player))
                    .isZero();
            String pending = game.getStoredValue("fracturedRealityPending_actor");
            assertThat(pending).startsWith("101_");
            FracturedRealityAcd2ButtonHandler.resolveFracturedRealityPlace(
                    player, game, event, "fracturedRealityPlace_gf");
            FracturedRealityAcd2ButtonHandler.resolveFracturedRealityPlace(
                    player, game, event, "fracturedRealityPlace_gf");
            units.verify(() -> AddUnitService.addUnits(event, tile, game, player.getColor(), "1 gf"), times(1));
            assertThat(game.getStoredValue("fracturedRealityPending_actor")).isEmpty();
        }
    }

    @Test
    void artifactResearchDiscardsRatherThanPurgesTheFragment() {
        player.addFragment("crf1");
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class)) {
            ArtifactResearchAcd2ButtonHandler.artifactResearchDiscard(
                    player, game, event, "artifactResearchDiscard_0_crf1");
            assertThat(player.getFragments()).isEmpty();
            assertThat(game.getExploreDiscard("cultural")).contains("crf1");
        }
    }

    @Test
    void artifactResearchAllowsGainWithoutMatchingResearchPrerequisites() {
        List<TechnologyModel> eligible = ArtifactResearchAcd2ButtonHandler.getEligibleTechs(player, game, 1);
        assertThat(eligible)
                .extracting(TechnologyModel::getAlias)
                .contains("dxa")
                .doesNotContain("fl", "lgf", "so");
        assertThat(eligible).allMatch(tech -> tech.getRequirements().orElse("").length() <= 1);
        assertThat(eligible).extracting(TechnologyModel::getAlias).doesNotHaveDuplicates();
        player.addTech("dxa");
        assertThat(ArtifactResearchAcd2ButtonHandler.getEligibleTechs(player, game, 1))
                .extracting(TechnologyModel::getAlias)
                .doesNotContain("dxa");
    }

    @Test
    void artifactResearchUsesGainButtonsRatherThanResearchButtons() {
        player.addFragment("crf1");
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var techs = mockStatic(ListTechService.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            ArtifactResearchAcd2ButtonHandler.artifactResearchDiscard(
                    player, game, event, "artifactResearchDiscard_0_crf1");
            techs.verify(() -> ListTechService.getTechButtons(anyList(), eq(player), eq("free")));
        }
    }

    @Test
    void artifactResearchCountsDiscardedFragmentsEvenWhenTheyShareAType() {
        player.addFragment("crf1");
        player.addFragment("crf2");
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var techs = mockStatic(ListTechService.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            ArtifactResearchAcd2ButtonHandler.artifactResearchDiscard(
                    player, game, event, "artifactResearchDiscard_0_crf1");
            ArtifactResearchAcd2ButtonHandler.artifactResearchDiscard(
                    player, game, event, "artifactResearchDiscard_1_crf2");
            assertThat(game.getExploreDiscard("cultural")).contains("crf1", "crf2");
            techs.verify(() -> ListTechService.getTechButtons(
                    org.mockito.ArgumentMatchers.argThat(
                            eligible -> eligible.stream().anyMatch(tech -> "fl".equals(tech.getAlias()))),
                    eq(player),
                    eq("free")));
        }
    }

    @Test
    void fracturedRealityRejectsAReplacementMoreExpensiveThanTheRoll() {
        game.setStoredValue("fracturedRealityPending_actor", "101_1");
        try (var units = mockStatic(AddUnitService.class)) {
            FracturedRealityAcd2ButtonHandler.resolveFracturedRealityPlace(
                    player, game, event, "fracturedRealityPlace_dn");
            units.verifyNoInteractions();
            assertThat(game.getStoredValue("fracturedRealityPending_actor")).isEqualTo("101_1");
        }
    }

    @Test
    void brokeredAlliancePaysOnceAndGrantsTheRevealedAbilityToTheChosenPlayer() {
        game.addPlayer("recipient", "Recipient");
        Player recipient = game.getPlayer("recipient");
        recipient.setFaction("hacan");
        recipient.setColor("yellow");
        player.setTg(5);
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var commanders = mockStatic(UnusedCommanderHelper.class)) {
            commanders
                    .when(() -> UnusedCommanderHelper.getUnusedCommander(game))
                    .thenReturn("jolnarcommander");
            BrokeredAllianceAcd2ButtonHandler.resolveBrokeredAlliance(player, game, event);
            BrokeredAllianceAcd2ButtonHandler.resolveBrokeredAlliance(player, game, event);
            assertThat(player.getTg()).isEqualTo(2);
            commanders.verify(() -> UnusedCommanderHelper.getUnusedCommander(game), times(1));
            BrokeredAllianceAcd2ButtonHandler.resolveBrokeredAllianceTarget(
                    player, game, event, "brokeredAllianceTarget_yellow");
            assertThat(recipient.hasLeader("jolnarcommander")).isTrue();
            assertThat(recipient.getLeader("jolnarcommander").orElseThrow().isLocked())
                    .isFalse();
            assertThat(player.hasLeader("jolnarcommander")).isFalse();
            assertThat(game.getStoredValue("brokeredAlliancePending_actor")).isEmpty();
        }
    }

    @Test
    void brokeredAllianceDoesNotRevealOrChargeWithoutEnoughTradeGoods() {
        player.setTg(2);
        try (var messages = mockStatic(MessageHelper.class);
                var commanders = mockStatic(UnusedCommanderHelper.class)) {
            BrokeredAllianceAcd2ButtonHandler.resolveBrokeredAlliance(player, game, event);
            commanders.verifyNoInteractions();
            assertThat(player.getTg()).isEqualTo(2);
        }
    }

    @Test
    void sacrificeDestroysOnlyThePlayersUnitsAcrossTheWholeSystem() {
        tile.addUnit("space", Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
        tile.addUnit("meer", Units.getUnitKey(UnitType.Infantry, player.getColorID()), 2);
        tile.addUnit("arinam", Units.getUnitKey(UnitType.Pds, player.getColorID()), 1);
        tile.addUnit("meer", Units.getUnitKey(UnitType.Infantry, "red"), 3);
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var secrets = mockStatic(DrawSecretService.class)) {
            SacrificeAcd2ButtonHandler.resolveSacrificeReward(player, game, event, "sacrificeReward_so_101");
            assertThat(tile.getSpaceUnitHolder().getUnitCount(UnitType.Carrier, player))
                    .isZero();
            assertThat(tile.getUnitHolders().get("meer").getUnitCount(UnitType.Infantry, player))
                    .isZero();
            assertThat(tile.getUnitHolders().get("arinam").getUnitCount(UnitType.Pds, player))
                    .isZero();
            assertThat(tile.getUnitHolders().get("meer").getUnitCount(Units.getUnitKey(UnitType.Infantry, "red")))
                    .isEqualTo(3);
            secrets.verify(() -> DrawSecretService.drawSO(event, game, player));
        }
    }

    @Test
    void sacrificeDoesNotGrantARewardFromAnEmptySystem() {
        try (var messages = mockStatic(MessageHelper.class);
                var buttons = mockStatic(ButtonHelper.class);
                var secrets = mockStatic(DrawSecretService.class);
                var destroy = mockStatic(DestroyUnitService.class)) {
            SacrificeAcd2ButtonHandler.resolveSacrificeReward(player, game, event, "sacrificeReward_so_101");
            secrets.verifyNoInteractions();
            destroy.verifyNoInteractions();
        }
    }

    @Test
    void deckListsMatchTheRevisedCardCatalog() {
        for (String deck : List.of("action_deck_2", "action_deck_2_pok", "action_deck_2_te", "action_deck_2_pok_te")) {
            List<String> cards = Mapper.getDeck(deck).getNewDeck();
            assertThat(cards).contains("concord", "scorched_earth").doesNotContain("hijacked_signal", "oracle");
            if (deck.endsWith("te")) {
                assertThat(cards).contains("brokered_alliance", "fractured_reality");
            } else {
                assertThat(cards).doesNotContain("brokered_alliance", "fractured_reality");
            }
        }
        assertThat(Mapper.getActionCard("hijacked_signal").getSource()).isEqualTo(ComponentSource.deprecated);
        assertThat(Mapper.getActionCard("oracle").getSource()).isEqualTo(ComponentSource.deprecated);
        assertThat(Mapper.getActionCard("fractured_reality").getSource()).isEqualTo(ComponentSource.action_deck_2);
    }
}
