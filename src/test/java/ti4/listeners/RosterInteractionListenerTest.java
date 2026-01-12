package ti4.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class RosterInteractionListenerTest {

    @Mock
    private ButtonInteractionEvent buttonInteractionEvent;

    @Mock
    private ModalInteractionEvent modalInteractionEvent;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private RosterMessageParser rosterMessageParser;

    @Mock
    private RosterUiBuilder rosterUiBuilder;

    @InjectMocks
    private RosterInteractionListener rosterInteractionListener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testHandleAddSelf() {
        when(buttonInteractionEvent.getButton().getCustomId()).thenReturn("roster_add_self");
        when(buttonInteractionEvent.getMessage().getContentRaw()).thenReturn("!roster add self");
        when(rosterMessageParser.parsePlayerIds(anyString())).thenReturn(Arrays.asList("123456789012345678"));
        when(rosterMessageParser.resolveMembers(any(), anyList())).thenReturn(Arrays.asList(mock(Member.class)));
        when(rosterUiBuilder.renderRoster(anyString(), anyList())).thenReturn("New Roster Content");

        rosterInteractionListener.onButtonInteraction(buttonInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(buttonInteractionEvent, "Added you to the roster.");
    }

    @Test
    public void testHandleRemoveSelf() {
        when(buttonInteractionEvent.getButton().getCustomId()).thenReturn("roster_remove_self");
        when(buttonInteractionEvent.getMessage().getContentRaw()).thenReturn("!roster remove self");
        when(rosterMessageParser.parsePlayerIds(anyString())).thenReturn(Arrays.asList("123456789012345678"));
        when(rosterMessageParser.resolveMembers(any(), anyList())).thenReturn(Arrays.asList(mock(Member.class)));
        when(rosterUiBuilder.renderRoster(anyString(), anyList())).thenReturn("New Roster Content");

        rosterInteractionListener.onButtonInteraction(buttonInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(buttonInteractionEvent, "Removed you from the roster.");
    }

    @Test
    public void testOpenAddSomeoneModal() {
        when(buttonInteractionEvent.getButton().getCustomId()).thenReturn("roster_add_someone");
        when(buttonInteractionEvent.getMessageId()).thenReturn("messageId");

        rosterInteractionListener.onButtonInteraction(buttonInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(buttonInteractionEvent, "Error processing modal.");
    }

    @Test
    public void testOpenRemoveSomeoneModal() {
        when(buttonInteractionEvent.getButton().getCustomId()).thenReturn("roster_remove_someone");
        when(buttonInteractionEvent.getMessageId()).thenReturn("messageId");

        rosterInteractionListener.onButtonInteraction(buttonInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(buttonInteractionEvent, "Error processing modal.");
    }

    @Test
    public void testOpenEditNameModal() {
        when(buttonInteractionEvent.getButton().getCustomId()).thenReturn("roster_edit_name");
        when(buttonInteractionEvent.getMessageId()).thenReturn("messageId");

        rosterInteractionListener.onButtonInteraction(buttonInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(buttonInteractionEvent, "Only bothelpers or admins can edit the game name for now.");
    }

    @Test
    public void testHandleAddSomeoneModal() {
        when(modalInteractionEvent.getModalId()).thenReturn("roster_add_someone~messageId");
        when(modalInteractionEvent.getValue("roster_add_someone_input").getAsString()).thenReturn("<@!123456789012345678>");
        when(rosterMessageParser.parsePlayerIds(anyString())).thenReturn(Arrays.asList("123456789012345678"));
        when(rosterMessageParser.resolveMembers(any(), anyList())).thenReturn(Arrays.asList(mock(Member.class)));
        when(rosterUiBuilder.renderRoster(anyString(), anyList())).thenReturn("New Roster Content");

        rosterInteractionListener.onModalInteraction(modalInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(modalInteractionEvent, "Added user to roster.");
    }

    @Test
    public void testHandleRemoveSomeoneModal() {
        when(modalInteractionEvent.getModalId()).thenReturn("roster_remove_someone~messageId");
        when(modalInteractionEvent.getValue("roster_remove_someone_input").getAsString()).thenReturn("<@!123456789012345678>");
        when(rosterMessageParser.parsePlayerIds(anyString())).thenReturn(Arrays.asList("123456789012345678"));
        when(rosterMessageParser.resolveMembers(any(), anyList())).thenReturn(Arrays.asList(mock(Member.class)));
        when(rosterUiBuilder.renderRoster(anyString(), anyList())).thenReturn("New Roster Content");

        rosterInteractionListener.onModalInteraction(modalInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(modalInteractionEvent, "Removed user from roster.");
    }

    @Test
    public void testHandleEditNameModal() {
        when(modalInteractionEvent.getModalId()).thenReturn("roster_edit_name~messageId");
        when(modalInteractionEvent.getValue("roster_edit_name_input").getAsString()).thenReturn("New Game Name");

        rosterInteractionListener.onModalInteraction(modalInteractionEvent);

        verify(messageHelper).sendEphemeralMessageToEventChannel(modalInteractionEvent, "Updated game name.");
    }
}