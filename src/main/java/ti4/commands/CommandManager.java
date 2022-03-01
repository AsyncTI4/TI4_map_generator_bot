package ti4.commands;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {
    private List<Command> commandList = new ArrayList<>();
    private static CommandManager manager = null;

    private CommandManager() {
    }

    public static CommandManager getInstance() {
        if (manager == null) {
            manager = new CommandManager();
        }
        return manager;
    }

    public List<Command> getCommandList() {
        return commandList;
    }

    public void addCommand(Command command) {
        commandList.add(command);
    }
}
