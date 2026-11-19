package ti4.discord.interactions.buttons.handlers.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.testUtils.BaseTi4Test;

// SPACE CANNON DEFENCE hits against landing ground forces get the same Auto-assign / Manually Assign / Cancel a Hit
// buttons as SPACE CANNON OFFENCE hits against ships, so a Maneuvering Jets play can cancel one of them. The cancel is
// owned by the player taking the hits and is offered once per roll (Maneuvering Jets ruling III).
class SpaceCannonDefenceHitsTest extends BaseTi4Test {

    private static final String POSITION = "304";
    private static final String PLANET = "lodor";

    private Game game;
    private Player sol;
    private Player nekro;
    private Tile tile;
    private MockedStatic<MessageHelper> messages;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("scd-hits-test");
        sol = game.addPlayer("200000000000000001", "Sol");
        sol.setFaction("sol");
        sol.setColor("blue");
        nekro = game.addPlayer("200000000000000002", "Nekro");
        nekro.setFaction("nekro");
        nekro.setColor("black");
        tile = new Tile("26", POSITION);
        game.setTile(tile);
        messages = mockStatic(MessageHelper.class);
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private static List<String> ids(List<Button> buttons) {
        return buttons.stream().map(Button::getCustomId).toList();
    }

    @Test
    void theAssignmentOffersAutoAssignManualAndAnOwnedCancel() {
        List<Button> buttons = SpaceCannonDefenceHitButtons.beforeACancel(nekro, tile, PLANET, 2);

        assertThat(ids(buttons))
                .containsExactly(
                        "FFCC_nekro_autoAssignGroundHits_lodor_2",
                        "getDamageButtons_304deleteThis_groundcombat",
                        "FFCC_nekro_cancelPdsDefenceHits_304_2_lodor");
        assertThat(buttons.stream().map(Button::getLabel))
                .containsExactly("Auto-assign Hits", "Manually Assign Hits", "Cancel a Hit");
    }

    @Test
    void noSecondCancelIsOfferedForTheSameRoll() {
        List<Button> buttons = SpaceCannonDefenceHitButtons.afterACancel(nekro, tile, PLANET, 1);

        assertThat(ids(buttons))
                .containsExactly("FFCC_nekro_autoAssignGroundHits_lodor_1", "getDamageButtons_304_groundcombat");
        assertThat(buttons.stream().map(Button::getLabel)).containsExactly("Auto-assign Hit", "Manually Assign Hit");
    }

    @Test
    void theRollPostsTheAssignmentToThePlayerTakingTheHits() {
        MessageChannel channel = mock(MessageChannel.class);
        Planet lodor = (Planet) tile.getUnitHolders().get(PLANET);

        CombatRollService.sendSpaceCannonDefenceHitAssignment(game, sol, nekro, tile, lodor, 2, channel);

        messages.verify(() -> MessageHelper.sendMessageToChannelWithButtons(
                eq(channel),
                contains("assign the SPACE CANNON DEFENCE hits"),
                argThat((List<Button> buttons) ->
                        ids(buttons).contains("FFCC_nekro_cancelPdsDefenceHits_304_2_lodor"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellingTakesOneHitOffAndLeavesNoFurtherCancel() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

        CombatButtonHandler.cancelSpaceCannonDefenceHits(event, nekro, "cancelPdsDefenceHits_304_2_lodor", game);

        messages.verify(() -> MessageHelper.sendMessageToChannel(any(), contains("canceled 1 hit")));
        ArgumentCaptor<CharSequence> text = ArgumentCaptor.forClass(CharSequence.class);
        verify(event.getMessage()).editMessage(text.capture());
        assertThat(text.getValue().toString()).endsWith("you may automatically assign the hit.");
        MessageEditAction edit = event.getMessage().editMessage(text.getValue());
        ArgumentCaptor<Collection<? extends MessageTopLevelComponent>> rows = ArgumentCaptor.forClass(Collection.class);
        verify(edit).setComponents(rows.capture());
        assertThat(rows.getValue().toString())
                .contains("autoAssignGroundHits_lodor_1")
                .doesNotContain("cancelPdsDefenceHits");
    }

    // Cancelling the only hit leaves nothing to assign: the assignment message goes away instead of offering an
    // "Auto-assign Hits" for 0 hits, which did nothing and left an AI seat pressing it forever.
    @Test
    void cancellingTheLastHitRemovesTheAssignment() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

        CombatButtonHandler.cancelSpaceCannonDefenceHits(event, nekro, "cancelPdsDefenceHits_304_1_lodor", game);

        messages.verify(() -> MessageHelper.sendMessageToChannel(any(), contains("canceled 1 hit")));
        verify(event.getHook()).deleteOriginal();
        verify(event.getMessage(), org.mockito.Mockito.never()).editMessage(any(CharSequence.class));
    }

    // An "Auto-assign Hits" for 0 hits left over on an older message clears itself.
    @Test
    void autoAssigningNoHitsClearsTheMessage() {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);

        CombatButtonHandler.autoAssignGroundHits(event, nekro, "autoAssignGroundHits_lodor_0", game);

        verify(event.getHook()).deleteOriginal();
    }

    @Test
    void parsesTheCancelIdWithOrWithoutTheFactionChecker() {
        CancelSpaceCannonDefenceHitsButtonId expected = new CancelSpaceCannonDefenceHitsButtonId("304", 2, "lodor");

        assertThat(CancelSpaceCannonDefenceHitsButtonId.parse("cancelPdsDefenceHits_304_2_lodor"))
                .isEqualTo(expected);
        assertThat(CancelSpaceCannonDefenceHitsButtonId.parse("FFCC_nekro_cancelPdsDefenceHits_304_2_lodor"))
                .isEqualTo(expected);
        assertThat(CancelSpaceCannonDefenceHitsButtonId.parse(expected.toButtonId()))
                .isEqualTo(expected);
    }

    @Test
    void longestRealisticIdFitsDiscordCustomIdLimit() {
        String factionChecker = "FFCC_" + "a".repeat(24) + "_";
        String id = factionChecker + CancelSpaceCannonDefenceHitsButtonId.of("tl123", 12, "custodiavigiliaplus");

        assertThat(id.length()).isLessThanOrEqualTo(100);
    }
}
