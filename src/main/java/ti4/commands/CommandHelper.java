package ti4.commands;

import java.util.Arrays;
import java.util.List;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public class CommandHelper {
    public static List<Choice> toChoices(String... values) {
        return toChoices(Arrays.asList(values));
    }

    public static List<Choice> toChoices(List<String> values) {
        return values.stream().map(v -> new Choice(v, v)).toList();
    }
}
