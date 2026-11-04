package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.executors.RunningTaskRegistry;

class RunningTasksTest {

    @Test
    void repliesPrivatelyWithoutMentionsOrBrokenCodeBlocks() {
        var event = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
        WebhookMessageEditAction<Message> reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(event.getHook().editOriginal(anyString())).thenReturn(reply);
        var command = new RunningTasks();
        assertThat(command.isEphemeral(event)).isTrue();
        try (var registration = RunningTaskRegistry.start("task ``` @everyone")) {
            command.execute(event);
        }
        var content = ArgumentCaptor.forClass(String.class);
        verify(event.getHook()).editOriginal(content.capture());
        assertThat(content.getValue()).hasSizeLessThanOrEqualTo(2000).contains("task ''' @everyone", "ms | RUNNABLE");
        verify(reply).setAllowedMentions(List.of());
        verify(reply, never()).setFiles(any(FileUpload.class));
    }

    @Test
    void attachesTheCompleteReportWhenItExceedsDiscordsMessageLimit() throws Exception {
        var event = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
        WebhookMessageEditAction<Message> reply = mock(WebhookMessageEditAction.class, RETURNS_SELF);
        when(event.getHook().editOriginal(anyString())).thenReturn(reply);
        try (var registration = RunningTaskRegistry.start("long task ".repeat(300))) {
            new RunningTasks().execute(event);
        }
        verify(event.getHook()).editOriginal("Currently running tasks are attached.");
        var attachment = ArgumentCaptor.forClass(FileUpload.class);
        verify(reply).setFiles(attachment.capture());
        try (FileUpload upload = attachment.getValue()) {
            assertThat(upload.getName()).isEqualTo("running-tasks.txt");
            assertThat(new String(upload.getData().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("long task ".repeat(300));
        }
    }

    @Test
    void emptySnapshotHasAnExplicitMessage() {
        assertThat(RunningTasks.formatReport(List.of())).contains("No tracked tasks are currently running");
    }
}
