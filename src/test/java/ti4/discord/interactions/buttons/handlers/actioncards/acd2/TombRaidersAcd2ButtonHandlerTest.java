package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;

class TombRaidersAcd2ButtonHandlerTest {

    private static final String PLANET = "testplanet";

    @Test
    void rollsHigherValueForPlanetWithTraitAndDrawsRelicOnATen() {
        String message = resolve(Set.of("cultural"), 2, 3, new ArrayDeque<>(List.of(4, 10, 1)), true);

        assertTrue(message.contains("rolled 3 dice"), message);
    }

    @Test
    void rollsCombinedValueForPlanetWithoutTraitAndSkipsRelicWithoutATen() {
        String message = resolve(Set.of(), 2, 3, new ArrayDeque<>(List.of(9, 9, 9, 9, 9)), false);

        assertTrue(message.contains("rolled 5 dice"), message);
    }

    @Test
    void offersOnlyActiveSystemPlanetsAndAnOtherButtonWhenThereIsAnActiveSystem() {
        List<String> ids =
                offeredButtonIds("resolveTombRaiders", "101", List.of("inactive", "active"), List.of("active"));

        assertEquals(List.of("fcresolveTombRaidersStep2_active", "fcresolveTombRaidersOther"), ids);
    }

    @Test
    void offersAllPlanetsWhenNoControlledPlanetIsInTheActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", "101", List.of("one", "two"), List.of());

        assertEquals(List.of("fcresolveTombRaidersStep2_one", "fcresolveTombRaidersStep2_two"), ids);
    }

    @Test
    void offersAllPlanetsWhenThereIsNoActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", null, List.of("one", "two"), List.of());

        assertEquals(List.of("fcresolveTombRaidersStep2_one", "fcresolveTombRaidersStep2_two"), ids);
    }

    @Test
    void omitsOtherButtonWhenEveryPlanetIsInTheActiveSystem() {
        List<String> ids = offeredButtonIds("resolveTombRaiders", "101", List.of("active"), List.of("active"));

        assertEquals(List.of("fcresolveTombRaidersStep2_active"), ids);
    }

    @Test
    void otherButtonOffersThePlanetsOutsideTheActiveSystem() {
        List<String> ids =
                offeredButtonIds("resolveTombRaidersOther", "101", List.of("inactive", "active"), List.of("active"));

        assertEquals(List.of("fcresolveTombRaidersStep2_inactive"), ids);
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

            if ("resolveTombRaidersOther".equals(handler)) {
                TombRaidersAcd2ButtonHandler.resolveTombRaidersOther(player, game, event);
            } else {
                TombRaidersAcd2ButtonHandler.resolveTombRaiders(player, game, event);
            }

            ArgumentCaptor<List<Button>> captor = ArgumentCaptor.captor();
            messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                    any(MessageChannel.class), anyString(), captor.capture()));
            return captor.getValue().stream().map(Button::getCustomId).toList();
        }
    }

    private static String resolve(
            Set<String> traits, int resources, int influence, Deque<Integer> rolls, boolean expectRelic) {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        Planet planet = mock(Planet.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(game.getPlanetsInfo()).thenReturn(Map.of(PLANET, planet));
        when(player.hasPlanet(PLANET)).thenReturn(true);
        when(player.getRepresentation()).thenReturn("player");
        when(player.getCorrectChannel()).thenReturn(mock(TextChannel.class));
        when(planet.getPlanetTypes()).thenReturn(traits);
        when(planet.getSumResourcesInfluence()).thenReturn(resources + influence);
        when(planet.getHigherofInfluenceOrResource()).thenReturn(Math.max(resources, influence));

        try (MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class);
                MockedStatic<ButtonHelper> buttons = mockStatic(ButtonHelper.class);
                MockedStatic<Helper> helper = mockStatic(Helper.class);
                MockedStatic<RelicHelper> relics = mockStatic(RelicHelper.class)) {
            helper.when(() -> Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(anyString(), any()))
                    .thenReturn("planet");

            DiceHelper.withForcedResults(
                    rolls,
                    () -> TombRaidersAcd2ButtonHandler.resolveTombRaidersStep2(
                            player, game, event, "resolveTombRaidersStep2_" + PLANET));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            messages.verify(() -> MessageHelper.sendMessageToChannel(any(MessageChannel.class), captor.capture()));
            if (expectRelic) {
                relics.verify(() -> RelicHelper.drawRelicAndNotify(player, event, game));
            } else {
                relics.verify(() -> RelicHelper.drawRelicAndNotify(any(), any(), any()), never());
            }
            return captor.getValue();
        }
    }
}
