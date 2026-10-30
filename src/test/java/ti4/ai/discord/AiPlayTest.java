package ti4.ai.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.testUtils.BaseTi4Test;

class AiPlayTest extends BaseTi4Test {

    @Test
    void acceptsEveryOfficialFactionSetupWithoutChoices() {
        assertThat(AiSettings.SUPPORTED_FACTIONS)
                .allSatisfy(
                        faction -> assertThat(AiPlay.factionRefusal(faction)).isNull());
    }

    @Test
    void refusesUnknownFactions() {
        assertThat(AiPlay.factionRefusal("notafaction")).contains("not a faction");
    }

    // The AIs only support Base, Prophecy of Kings and Thunder's Edge games, so a Discordant Stars faction would make
    // the game unsupported for them.
    @Test
    void refusesHomebrewFactions() {
        assertThat(AiPlay.factionRefusal("kollecc")).contains("Base, Prophecy of Kings and Thunder's Edge");
    }

    @Test
    void picksARandomSupportedFactionByDefault() {
        assertThat(AiSettings.SUPPORTED_FACTIONS).contains(AiPlay.randomFaction());
    }
}
