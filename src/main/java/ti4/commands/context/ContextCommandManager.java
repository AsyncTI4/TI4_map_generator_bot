package ti4.commands.context;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContextCommandManager {

    private static final Map<String, ContextCommand<?>> commands = Stream.of(new BanUser(), new DeleteMessage())
            .collect(Collectors.toMap(ContextCommand::getName, Function.identity()));

    public static ContextCommand<?> getCommand(String name) {
        return commands.get(name);
    }

    public static Collection<ContextCommand<?>> getCommands() {
        return commands.values();
    }
}
