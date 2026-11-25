package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// Political Secret: "When an agenda is revealed: The <color> player cannot vote, play action cards, or use faction
// abilities until after that agenda has been resolved." Quash is a faction ability, so once another player plays
// Xxcha's Political Secret, Xxcha must not be offered Quash for that agenda (Elder Qanoj ruling III: the commander
// restores voting, not Quash). The silence ends with the agenda: a replacement agenda clears it (Quash ruling I.α).
class PoliticalSecretQuashTest extends BaseTi4Test {

    private static final String QUEUE_QUASH = "queueWhen_ability_quash";

    private Game game;
    private Player xxcha;
    private Player holder;
    private ButtonInteractionEvent event;
    private MockedStatic<MessageHelper> messageHelper;
    private MockedStatic<ButtonHelper> buttonHelper;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("political-secret-quash-test");
        game.setPhaseOfGame("agendawaiting");
        xxcha = game.addPlayer("xxcha-id", "xxcha");
        xxcha.setFaction("xxcha");
        xxcha.setColor("red");
        xxcha.addAbility("quash");
        xxcha.setStrategicCC(2);
        xxcha.addOwnedPromissoryNoteByID("red_ps");
        holder = game.addPlayer("holder-id", "holder");
        holder.setFaction("sol");
        holder.setColor("blue");
        holder.setPromissoryNote("red_ps");
        event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        messageHelper = mockStatic(MessageHelper.class);
        buttonHelper = mockStatic(ButtonHelper.class);
    }

    @AfterEach
    void tearDown() {
        buttonHelper.close();
        messageHelper.close();
    }

    private boolean quashButtonOffered() {
        return AgendaWhensAftersHelper.getPossibleWhenButtons(xxcha).stream()
                .map(Button::getCustomId)
                .anyMatch(QUEUE_QUASH::equals);
    }

    private void holderPlaysXxchasPoliticalSecret() {
        PromissoryNoteHelper.resolvePNPlay("red_ps", holder, game, event);
    }

    @Test
    void offersQuashBeforeThePoliticalSecretIsPlayed() {
        assertThat(quashButtonOffered()).isTrue();
        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(xxcha)).contains("Quash");
        assertThat(AgendaWhensAftersHelper.silencedByPoliticalSecret(xxcha)).isFalse();
    }

    @Test
    void politicalSecretTakesQuashOutOfTheWhenOptions() {
        holderPlaysXxchasPoliticalSecret();

        assertThat(AgendaWhensAftersHelper.silencedByPoliticalSecret(xxcha)).isTrue();
        assertThat(quashButtonOffered()).isFalse();
        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(xxcha)).doesNotContain("Quash");
        assertThat(AgendaWhensAftersHelper.canQuash(xxcha)).isFalse();
        assertThat(AgendaWhensAftersHelper.silencedByPoliticalSecret(holder)).isFalse();
    }

    // A Quash button posted before the note resolved must not still discard the agenda or spend a token.
    @Test
    void aQuashButtonPressedAfterThePoliticalSecretDoesNothing() {
        holderPlaysXxchasPoliticalSecret();

        ButtonHelperFactionSpecific.quash(event, xxcha, game);

        assertThat(xxcha.getStrategicCC()).isEqualTo(2);
        assertThat(game.getStoredValue("agendaCount")).isEmpty();
    }

    // With the Scepter of Emelpar Xxcha could otherwise pay for Quash from reinforcements; the note still blocks it.
    @Test
    void politicalSecretBlocksAScepterPaidQuashToo() {
        xxcha.setStrategicCC(0);
        xxcha.addRelic("emelpar");
        assertThat(quashButtonOffered()).isTrue();

        holderPlaysXxchasPoliticalSecret();

        assertThat(quashButtonOffered()).isFalse();
    }

    // Before the fix, opening the when menu removed Xxcha from declinedWhens, undoing the note's auto-pass and
    // offering Quash again.
    @Test
    void openingTheWhenMenuDoesNotUndoTheAutoPass() {
        holderPlaysXxchasPoliticalSecret();
        assertThat(game.getStoredValue("declinedWhens")).contains("xxcha_");

        AgendaWhensAftersHelper.queueAWhen(game, event, xxcha);

        assertThat(game.getStoredValue("declinedWhens")).contains("xxcha_");
    }

    // A new agenda (for example the one revealed after a Quash or Veto) starts with fresh queues, and the note no
    // longer silences Xxcha.
    @Test
    void theNextAgendaClearsTheSilence() {
        holderPlaysXxchasPoliticalSecret();

        AgendaWhensAftersHelper.eraseAgendaQueues(null, game);

        assertThat(AgendaWhensAftersHelper.silencedByPoliticalSecret(xxcha)).isFalse();
        assertThat(quashButtonOffered()).isTrue();
    }
}
