package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.service.testbed.TestBedPress.Recorder;
import ti4.testUtils.BaseTi4Test;

// The stand-in click a script uses to press a button as a seat.
class TestBedPressTest extends BaseTi4Test {

    private final Button passButton = Button.danger("FFCC_nekro_passForRound", "Pass");
    private Message message;
    private MessageChannelUnion channel;
    private Member developer;
    private User developerUser;

    @BeforeEach
    void setUp() {
        channel = mock(MessageChannelUnion.class);
        when(channel.getId()).thenReturn("4242");
        message = mock(Message.class);
        when(message.getJDA()).thenReturn(mock(JDA.class));
        when(message.getChannel()).thenReturn(channel);
        when(message.getId()).thenReturn("99");
        when(message.getIdLong()).thenReturn(99L);
        developerUser = mock(User.class);
        developer = mock(Member.class);
        when(developer.getUser()).thenReturn(developerUser);
        when(developer.getId()).thenReturn("111");
    }

    // A step may name a button by label (any case), full id, or id without the FFCC check; an id prefix is weaker,
    // so card 1 is never confused with card 12.
    @Test
    void matchesButtons() {
        assertEquals(TestBedPress.Match.EXACT, TestBedPress.match(passButton, "pass"));
        assertEquals(TestBedPress.Match.EXACT, TestBedPress.match(passButton, "passForRound"));
        assertEquals(TestBedPress.Match.PREFIX, TestBedPress.match(passButton, "passFor"));
        assertEquals(TestBedPress.Match.NONE, TestBedPress.match(passButton, "turnEnd"));
        assertEquals(
                TestBedPress.Match.PREFIX,
                TestBedPress.match(Button.danger("ac_play_from_hand_12", "(12) Sabotage"), "ac_play_from_hand_1"));
    }

    // The stand-in looks like a real press on that message, by the developer, in that channel, and captures replies
    // and opened modals without reaching Discord; edits to the pressed message are applied to it.
    @Test
    void standInBehavesLikeAClick() {
        Recorder recorder = new Recorder();
        ButtonInteractionEvent event = TestBedPress.standInEvent(message, passButton, developer, recorder);
        assertSame(message, event.getMessage());
        assertSame(channel, event.getChannel());
        assertSame(developer, event.getMember());
        assertSame(developerUser, event.getUser());

        event.deferEdit().queue();
        event.getHook()
                .sendMessage("these buttons are for someone else")
                .setEphemeral(true)
                .queue();
        event.replyModal(Modal.create("tradeModal_nekro", "Trade")
                        .addComponents(Label.of(
                                "Amount",
                                TextInput.create("amount", TextInputStyle.SHORT).build()))
                        .build())
                .queue();
        assertEquals(List.of("these buttons are for someone else"), recorder.replies());
        assertEquals("tradeModal_nekro", recorder.modalId());

        // Handlers that update their own message (tactical movement does) must change the real message.
        MessageEditAction edit = mock(MessageEditAction.class, RETURNS_SELF);
        when(message.editMessage(any(CharSequence.class))).thenReturn(edit);
        event.getHook().editOriginal("Choose a system").setComponents(List.of()).queue();
        verify(edit).setComponents(List.of());
        verify(edit).queue();
    }

    // Guard for JDA upgrades: every no-argument method answers without being "unsupported", and JDA's own helper
    // methods run for real (answering them with null once crashed a live script on getTimeCreated()).
    @Test
    void standInAnswersEveryJdaMethod() throws Exception {
        Recorder recorder = new Recorder();
        ButtonInteraction interaction = TestBedPress.standInEvent(message, passButton, developer, recorder)
                .getInteraction();
        for (Method method : ButtonInteraction.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() > 0) continue;
            method.invoke(interaction);
        }
        assertEquals(List.of(), recorder.unsupportedCalls());
        assertNotNull(interaction.getTimeCreated());
    }
}
