package ti4.spring.service.usage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InteractionCountServiceTest {

    @Test
    void incrementSlashCommandUpdatesExistingRowWithoutInsert() {
        SlashCommandCountRepository repository = mock(SlashCommandCountRepository.class);
        when(repository.incrementExisting("/bothelper list_slash_commands_used", LocalDate.now().toString()))
                .thenReturn(1);

        InteractionCountService service = new InteractionCountService(repository);
        service.incrementSlashCommand("/bothelper list_slash_commands_used");

        verify(repository).incrementExisting("/bothelper list_slash_commands_used", LocalDate.now().toString());
        verify(repository, never()).insertCount("/bothelper list_slash_commands_used", LocalDate.now().toString());
    }

    @Test
    void incrementSlashCommandInsertsWhenNoRowExists() {
        SlashCommandCountRepository repository = mock(SlashCommandCountRepository.class);
        when(repository.incrementExisting("/developer button_processing_statistics", LocalDate.now().toString()))
                .thenReturn(0);

        InteractionCountService service = new InteractionCountService(repository);
        service.incrementSlashCommand("/developer button_processing_statistics");

        verify(repository).incrementExisting("/developer button_processing_statistics", LocalDate.now().toString());
        verify(repository).insertCount("/developer button_processing_statistics", LocalDate.now().toString());
    }
}
