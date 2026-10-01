package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    // A step may name a button by label (any case), full id, or id without the FFCC faction check; an id prefix is
    // a weaker match, used only when nothing matches exactly.
    @ParameterizedTest(name = "`{0}` -> {1}")
    @CsvSource({
        "Pass, EXACT",
        "pass, EXACT",
        "FFCC_nekro_passForRound, EXACT",
        "passForRound, EXACT",
        "FFCC_nekro_pass, PREFIX",
        "passFor, PREFIX",
        "pass_, NONE",
        "turnEnd, NONE"
    })
    void matchesButtons(String target, TestBedPress.Match expected) {
        assertEquals(expected, TestBedPress.match(passButton, target));
    }

    // Numbered hand buttons share prefixes: card 1 must not be confused with card 12.
    @Test
    void exactIdBeatsALongerIdWithTheSamePrefix() {
        assertEquals(
                TestBedPress.Match.PREFIX,
                TestBedPress.match(Button.danger("ac_play_from_hand_12", "(12) Sabotage"), "ac_play_from_hand_1"));
        assertEquals(
                TestBedPress.Match.EXACT,
                TestBedPress.match(Button.danger("ac_play_from_hand_1", "(1) Sabotage"), "ac_play_from_hand_1"));
    }

    // ButtonContext reads the game, the acting seat and saveButtons from exactly these, so the stand-in must
    // look like a real press on that message, by the developer, in that channel.
    @Test
    void standInEventPointsAtTheRealMessage() {
        ButtonInteractionEvent event = TestBedPress.standInEvent(message, passButton, developer, new Recorder());

        assertSame(passButton, event.getButton());
        assertEquals("FFCC_nekro_passForRound", event.getComponentId());
        assertSame(message, event.getMessage());
        assertEquals("99", event.getMessageId());
        assertSame(channel, event.getChannel());
        assertSame(developer, event.getMember());
        assertSame(developerUser, event.getUser());
        assertTrue(event.isAcknowledged());
    }

    // Replies and opened modals are captured so a script can assert on them; nothing reaches Discord.
    @Test
    void recordsRepliesAndModals() {
        Recorder recorder = new Recorder();
        ButtonInteractionEvent event = TestBedPress.standInEvent(message, passButton, developer, recorder);

        event.deferEdit().queue();
        event.getHook()
                .sendMessage("these buttons are for someone else")
                .setEphemeral(true)
                .queue();
        event.reply("plain reply").queue();
        event.replyModal(Modal.create("tradeModal_nekro", "Trade")
                        .addComponents(Label.of(
                                "Amount",
                                TextInput.create("amount", TextInputStyle.SHORT).build()))
                        .build())
                .queue();

        assertEquals(List.of("these buttons are for someone else", "plain reply"), recorder.replies());
        assertEquals("tradeModal_nekro", recorder.modalId());
        assertTrue(
                recorder.unsupportedCalls().isEmpty(),
                recorder.unsupportedCalls().toString());
    }

    // Guard for JDA upgrades: every no-argument method a ButtonInteraction must implement is answered by the
    // stand-in. A new abstract method in JDA shows up here instead of as "unsupported" in a live script report.
    @Test
    void standInAnswersEveryRequiredMethod() throws Exception {
        Recorder recorder = new Recorder();
        ButtonInteraction interaction = TestBedPress.standInEvent(message, passButton, developer, recorder)
                .getInteraction();
        List<String> checked = new ArrayList<>();
        for (Method method : ButtonInteraction.class.getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers()) || method.getParameterCount() > 0) {
                continue;
            }
            method.invoke(interaction);
            checked.add(method.getName());
        }
        assertTrue(checked.size() > 10, "expected many abstract methods, got " + checked);
        assertEquals(List.of(), recorder.unsupportedCalls(), "checked " + Arrays.toString(checked.toArray()));
    }

    // JDA's own helper (default) methods must run for real, not be answered with null: a handler calling
    // `event.getTimeCreated()` crashed a live script with a NullPointerException before this was fixed.
    @Test
    void jdaHelperMethodsRunForReal() throws Exception {
        ButtonInteraction interaction = TestBedPress.standInEvent(message, passButton, developer, new Recorder())
                .getInteraction();
        assertNotNull(interaction.getTimeCreated());
        for (Method method : ButtonInteraction.class.getMethods()) {
            if (!method.isDefault() || method.getParameterCount() > 0) continue;
            method.invoke(interaction);
        }
    }
}
