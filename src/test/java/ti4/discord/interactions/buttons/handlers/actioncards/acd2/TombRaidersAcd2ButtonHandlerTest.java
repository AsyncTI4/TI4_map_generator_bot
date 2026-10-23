package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.async.DieRollSequenceService;

class TombRaidersAcd2ButtonHandlerTest {

    private static final String PLANET = "testplanet";

    @Test
    void stopsAtTheFirstTenAndOffersRelicAndKeepRollingButtons() {
        Outcome outcome = resolve(Set.of("cultural"), 2, 3, new ArrayDeque<>(List.of(4, 10, 1)));

        assertEquals(2, outcome.rolls().size());
        assertEquals(2, outcome.activities().size());
        assertTrue(outcome.header().contains("up to 3 dice"), outcome.header());
        assertEquals(List.of("fcdrawRelic", "fctombRaidersKeepRolling_1_testplanet"), outcome.buttonIds());
    }

    @Test
    void omitsKeepRollingWhenTheTenIsTheLastDie() {
        Outcome outcome = resolve(Set.of("cultural"), 2, 3, new ArrayDeque<>(List.of(4, 5, 10)));

        assertEquals(3, outcome.rolls().size());
        assertEquals(List.of("fcdrawRelic"), outcome.buttonIds());
    }

    @Test
    void rollsCombinedValueForPlanetWithoutTraitAndOffersNoButtonsWithoutATen() {
        Outcome outcome = resolve(Set.of(), 2, 3, new ArrayDeque<>(List.of(9, 9, 9, 9, 9)));

        assertEquals(5, outcome.rolls().size());
        assertTrue(outcome.header().contains("up to 5 dice"), outcome.header());
        assertTrue(outcome.buttonIds().isEmpty());
        assertEquals(5, Set.copyOf(outcome.activities()).size());
    }

    @Test
    void keepRollingResumesWithTheRemainingDice() {
        Outcome outcome =
                resolve(Set.of(), 0, 0, new ArrayDeque<>(List.of(1, 10, 1)), "tombRaidersKeepRolling_3_" + PLANET);

        assertEquals(2, outcome.rolls().size());
        assertTrue(outcome.header().contains("keeps rolling up to 3 dice"), outcome.header());
        assertEquals(List.of("fcdrawRelic", "fctombRaidersKeepRolling_1_testplanet"), outcome.buttonIds());
    }

    @Test
    void neverRunsOutOfActivitiesForLargeRolls() {
        Outcome outcome = resolve(Set.of("cultural"), 50, 0, new ArrayDeque<>(java.util.Collections.nCopies(50, 1)));

        assertEquals(50, outcome.activities().size());
    }

    @Test
    void offersOnlyActiveSystemPlanetsAndAnOtherButtonWhenThereIsAnActiveSystem() {
        List<String> ids =
                offeredButtonIds("resolveTombRaiders", "101", List.of("inactive", "active"), List.of("active"));

        assertEquals(List.of("fctombRaidersPlanet_active", "fctombRaidersOther"), ids);
    }

    @Test
    void offersAllPlanetsWhenNoControlledPlanetIsInTheActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", "101", List.of("one", "two"), List.of());

        assertEquals(List.of("fctombRaidersPlanet_one", "fctombRaidersPlanet_two"), ids);
    }

    @Test
    void offersAllPlanetsWhenThereIsNoActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", null, List.of("one", "two"), List.of());

        assertEquals(List.of("fctombRaidersPlanet_one", "fctombRaidersPlanet_two"), ids);
    }

    @Test
    void omitsOtherButtonWhenEveryPlanetIsInTheActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", "101", List.of("active"), List.of("active"));

        assertEquals(List.of("fctombRaidersPlanet_active"), ids);
    }

    @Test
    void otherButtonOffersThePlanetsOutsideTheActiveSystem() {
        List<String> ids =
                offeredButtonIds("tombRaidersOther", "101", List.of("inactive", "active"), List.of("active"));

        assertEquals(List.of("fctombRaidersPlanet_inactive"), ids);
    }

    private static List<String> offeredButtonIds(
            String handler, String activeSystem, List<String> playerPlanets, List<String> activeSystemPlanets) {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        Tile tile = mock(Tile.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        List<Planet> tilePlanets = activeSystemPlanets.stream()
                .map(name -> {
                    Planet planet = mock(Planet.class);
                    when(planet.getName()).thenReturn(name);
                    return planet;
                })
                .toList();
        when(game.getCurrentActiveSystem()).thenReturn(activeSystem);
        when(game.getTileByPosition(activeSystem)).thenReturn(activeSystem == null ? null : tile);
        when(tile.getPlanetUnitHolders()).thenReturn(tilePlanets);
        when(player.getPlanets()).thenReturn(playerPlanets);
        playerPlanets.forEach(name -> when(player.hasPlanet(name)).thenReturn(true));
        when(player.factionButtonChecker()).thenReturn("fc");
        when(player.getRepresentation()).thenReturn("player");
        when(player.getCorrectChannel()).thenReturn(mock(TextChannel.class));

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<ButtonHelper> buttons = mockStatic(ButtonHelper.class);
                MockedStatic<Helper> helper = mockStatic(Helper.class)) {
            helper.when(() -> Helper.getPlanetRepresentation(anyString(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            if ("tombRaidersOther".equals(handler)) {
                TombRaidersAcd2ButtonHandler.tombRaidersOther(player, game, event);
            } else {
                TombRaidersAcd2ButtonHandler.resolveTombRaiders(player, game, event);
            }

            ArgumentCaptor<List<Button>> captor = ArgumentCaptor.captor();
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    any(MessageChannel.class), anyString(), captor.capture()));
            return captor.getValue().stream().map(Button::getCustomId).toList();
        }
    }

    private record Outcome(
            String header, List<DiceHelper.Die> rolls, List<String> activities, List<String> buttonIds) {}

    private static Outcome resolve(Set<String> traits, int resources, int influence, Deque<Integer> rolls) {
        return resolve(traits, resources, influence, rolls, "tombRaidersPlanet_" + PLANET);
    }

    private static Outcome resolve(
            Set<String> traits, int resources, int influence, Deque<Integer> rolls, String buttonId) {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        Planet planet = mock(Planet.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(game.getPlanetsInfo()).thenReturn(Map.of(PLANET, planet));
        when(player.hasPlanet(PLANET)).thenReturn(true);
        when(player.getRepresentation()).thenReturn("player");
        when(player.factionButtonChecker()).thenReturn("fc");
        when(player.getCorrectChannel()).thenReturn(mock(TextChannel.class));
        when(planet.getPlanetTypes()).thenReturn(traits);
        when(planet.getSumResourcesInfluence()).thenReturn(resources + influence);
        when(planet.getHigherofInfluenceOrResource()).thenReturn(Math.max(resources, influence));

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<ButtonHelper> buttons = mockStatic(ButtonHelper.class);
                MockedStatic<Helper> helper = mockStatic(Helper.class);
                MockedStatic<DieRollSequenceService> sequence = mockStatic(DieRollSequenceService.class)) {
            helper.when(() -> Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(anyString(), any()))
                    .thenReturn("planet");

            DiceHelper.withForcedResults(rolls, () -> {
                if (buttonId.startsWith("tombRaidersKeepRolling_")) {
                    TombRaidersAcd2ButtonHandler.tombRaidersKeepRolling(player, game, event, buttonId);
                } else {
                    TombRaidersAcd2ButtonHandler.tombRaidersPlanet(player, game, event, buttonId);
                }
            });

            ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<List<String>> activities = ArgumentCaptor.captor();
            ArgumentCaptor<List<DiceHelper.Die>> dice = ArgumentCaptor.captor();
            ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
            sequence.verify(() -> DieRollSequenceService.play(
                    any(MessageChannel.class),
                    header.capture(),
                    activities.capture(),
                    dice.capture(),
                    completion.capture()));

            completion.getValue().run();

            ArgumentCaptor<List<Button>> offered = ArgumentCaptor.captor();
            messages.verify(
                    () -> MessageHelper.sendMessageToChannelWithButtons(
                            any(MessageChannel.class), anyString(), offered.capture()),
                    atLeast(0));
            List<String> buttonIds = offered.getAllValues().stream()
                    .flatMap(List::stream)
                    .map(Button::getCustomId)
                    .toList();
            return new Outcome(header.getValue(), dice.getValue(), activities.getValue(), buttonIds);
        }
    }
}
