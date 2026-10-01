package ti4.discord.interactions.commands.special;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SearchWinningPathOptionCountTest {

    private static final int DISCORD_OPTION_LIMIT = 25;

    @Test
    void usesEveryOptionSlotDiscordAllows() {
        assertEquals(DISCORD_OPTION_LIMIT, new SearchWinningPath().getOptions().size());
    }
}
