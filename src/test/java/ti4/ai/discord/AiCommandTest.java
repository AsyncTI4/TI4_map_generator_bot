package ti4.ai.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiSettings;
import ti4.image.Mapper;

class AiCommandTest {

    // Slash commands are built at startup before Mapper has loaded the game data. Reading Mapper while building
    // /ai would throw and abort command registration for the whole guild.
    @Test
    void buildsWithoutReadingGameData() {
        try (MockedStatic<Mapper> ignored = Mockito.mockStatic(Mapper.class, invocation -> {
            throw new AssertionError("Mapper." + invocation.getMethod().getName() + " was called while building /ai");
        })) {
            AiCommand command = new AiCommand();

            assertThat(command.getSubcommands())
                    .containsKeys("add", "status", "pause", "resume", "delegate", "remove", "watch");
        }
    }

    @Test
    void offersEverySupportedFactionAsAChoice() {
        AiAdd add = new AiAdd();

        assertThat(add.getOptions())
                .filteredOn(option -> "faction".equals(option.getName()))
                .singleElement()
                .satisfies(option -> assertThat(option.getChoices())
                        .extracting(choice -> choice.getAsString())
                        .containsExactlyElementsOf(AiSettings.SUPPORTED_FACTIONS));
    }

    // /ai watch seats 3 to 6 AIs: below 3 the bot's setup refuses to start, above 6 the standard map has no homes.
    @Test
    void watchOffersThreeToSixSeats() {
        AiWatch watch = new AiWatch();

        assertThat(watch.getOptions())
                .filteredOn(option -> "seats".equals(option.getName()))
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.getMinValue()).isEqualTo(3L);
                    assertThat(option.getMaxValue()).isEqualTo(6L);
                });
    }
}
