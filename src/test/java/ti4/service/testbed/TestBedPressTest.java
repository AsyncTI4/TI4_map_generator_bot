package ti4.service.testbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        // Card 1 missing must not press card 12: a prefix may not stop in the middle of a number
        assertEquals(
                TestBedPress.Match.NONE,
                TestBedPress.match(Button.danger("ac_play_from_hand_12", "(12) Sabotage"), "ac_play_from_hand_1"));
        assertEquals(
                TestBedPress.Match.PREFIX,
                TestBedPress.match(Button.danger("strategicAction_3_x", "Play"), "strategicAction_3"));
        // Faction ids may contain underscores (pi_hacan); the whole faction must be stripped, not just "pi_"
        assertEquals(
                TestBedPress.Match.EXACT,
                TestBedPress.match(Button.danger("FFCC_pi_hacan_turnEnd", "End Turn"), "turnEnd"));
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

    // A real player sees these buttons in an ephemeral reply; the stand-in cannot send one, so it re-posts the
    // message in the seat's own channel with the exact same button ids (FFCC prefix included).
    @Test
    void ephemeralButtonsAreRepostedWithIdenticalIds() {
        Recorder recorder = new Recorder();
        MessageChannel seatChannel = mock(MessageChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message reposted = mock(Message.class);
        when(reposted.getJumpUrl()).thenReturn("https://discord.com/channels/1/2/3");
        when(create.complete()).thenReturn(reposted);
        ArgumentCaptor<MessageCreateData> sent = ArgumentCaptor.forClass(MessageCreateData.class);
        when(seatChannel.sendMessage(sent.capture())).thenReturn(create);
        Button generic = Button.secondary("FFCC_nekro_componentActionRes_generic_", "Generic Component Action");

        ButtonInteractionEvent event = TestBedPress.standInEvent(
                message, passButton, developer, recorder, new TestBedPress.Repost(seatChannel, "nekro"));
        MessageCreateData menu = new MessageCreateBuilder()
                .setContent("Choose a component action")
                .setComponents(ActionRow.of(generic))
                .build();
        event.getHook().setEphemeral(true).sendMessage(menu).queue();

        List<Button> buttons = sent.getValue().getComponentTree().findAll(Button.class);
        assertEquals(
                List.of("FFCC_nekro_componentActionRes_generic_"),
                buttons.stream().map(Button::getCustomId).toList());
        assertTrue(sent.getValue().getContent().endsWith("Choose a component action"));
        assertEquals(List.of("https://discord.com/channels/1/2/3"), recorder.reposts());
        assertEquals(List.of("Choose a component action"), recorder.replies());
    }

    // event.reply(...) goes through deferReply(); its success callback gets the hook, as with a real reply.
    @Test
    void ephemeralReplyWithButtonsIsRepostedAndCallsBack() {
        Recorder recorder = new Recorder();
        MessageChannel seatChannel = mock(MessageChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        when(create.complete()).thenReturn(mock(Message.class));
        when(seatChannel.sendMessage(any(MessageCreateData.class))).thenReturn(create);
        ButtonInteractionEvent event = TestBedPress.standInEvent(
                message, passButton, developer, recorder, new TestBedPress.Repost(seatChannel, "nekro"));

        AtomicReference<Object> callback = new AtomicReference<>();
        event.reply("Pick a card")
                .setEphemeral(true)
                .addComponents(ActionRow.of(Button.primary("FFCC_nekro_ac_play_from_hand_12", "Sabotage")))
                .queue(callback::set);

        ArgumentCaptor<MessageCreateData> sent = ArgumentCaptor.forClass(MessageCreateData.class);
        verify(seatChannel).sendMessage(sent.capture());
        assertTrue(
                sent.getValue().getContent().endsWith("Pick a card"),
                sent.getValue().getContent());
        assertTrue(callback.get() instanceof InteractionHook);
    }

    // Plain ephemeral text stays a recorded reply; nothing is posted.
    @Test
    void ephemeralTextIsNotReposted() {
        Recorder recorder = new Recorder();
        MessageChannel seatChannel = mock(MessageChannel.class);
        ButtonInteractionEvent event = TestBedPress.standInEvent(
                message, passButton, developer, recorder, new TestBedPress.Repost(seatChannel, "nekro"));

        event.getHook()
                .setEphemeral(true)
                .sendMessage("these buttons are for someone else")
                .queue();

        verify(seatChannel, never()).sendMessage(any(MessageCreateData.class));
        assertEquals(List.of("these buttons are for someone else"), recorder.replies());
        assertEquals(List.of(), recorder.reposts());
    }

    // A press only passes when the handler ran for that seat: a thrown handler or a "not yours" reply fails it.
    @Test
    void classifiesWhatAPressDid() {
        assertEquals(TestBedPress.Outcome.PRESSED, TestBedPress.outcome(false, new Recorder()));
        assertEquals(TestBedPress.Outcome.HANDLER_FAILED, TestBedPress.outcome(true, new Recorder()));

        Recorder refused = new Recorder();
        refused.reply("To <:sol:>: these buttons are for someone else");
        assertEquals(TestBedPress.Outcome.REJECTED, TestBedPress.outcome(false, refused));

        Recorder form = new Recorder();
        form.modal("tradeModal_nekro");
        assertEquals(TestBedPress.Outcome.FORM_OPENED, TestBedPress.outcome(false, form));
        assertTrue(new TestBedPress.PressResult(TestBedPress.Outcome.FORM_OPENED, "", form).pressed());
        assertFalse(new TestBedPress.PressResult(TestBedPress.Outcome.REJECTED, "", refused).pressed());
    }

    // Exact beats prefix, the acting seat's buttons beat another seat's, and among equals the newest message wins.
    @Test
    void picksTheButtonMeantForTheSeat() {
        MessageChannel cardsInfo = mock(MessageChannel.class);
        MessageChannel main = mock(MessageChannel.class);
        Message olderOwn = buttonMessage(10, Button.danger("FFCC_nekro_turnEnd", "End Turn"));
        Message newerForeign = buttonMessage(30, Button.danger("FFCC_sol_turnEnd", "End Turn"));
        Message newestOwn = buttonMessage(20, Button.danger("FFCC_nekro_turnEnd", "End Turn"));
        Map<MessageChannel, List<Message>> history = Map.of(
                cardsInfo, List.of(olderOwn),
                main, List.of(newerForeign, newestOwn));

        List<String> seen = new ArrayList<>();
        TestBedPress.Found found = TestBedPress.find(List.of(cardsInfo, main), history::get, "End Turn", "nekro", seen);

        assertNotNull(found);
        assertSame(newestOwn, found.message());
        assertEquals(1, found.otherMatches());
        assertTrue(seen.contains("End Turn (`turnEnd`, for sol)"), seen.toString());
    }

    private static Message buttonMessage(long id, Button button) {
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(id);
        when(message.getComponentTree()).thenReturn(MessageComponentTree.of(ActionRow.of(button)));
        return message;
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
