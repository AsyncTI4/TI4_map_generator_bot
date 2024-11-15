package ti4.commands;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    public static final Map<String, Command> commands = new HashMap<>();

    public static void addCommand(Command command) {
        commands.put(command.getName(), command);
    }

    public static Command getCommand(String name) {
        return commands.get(name);
    }

    public static Collection<Command> getCommands() {
        return commands.values();
    }
}
