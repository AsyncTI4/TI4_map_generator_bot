package ti4.commands.franken;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;

class DraftLimitsTest {

    @Test
    void registersBreakthroughLimitOption() {
        DraftLimits command = new DraftLimits();

        assertTrue(
                command.getOptions().stream().anyMatch(option -> Constants.BREAKTHROUGH_LIMIT.equals(option.getName())),
                "Draft limits should expose a breakthrough limit option");
    }
}
