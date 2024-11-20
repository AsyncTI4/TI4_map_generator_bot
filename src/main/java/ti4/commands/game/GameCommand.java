package ti4.commands.game;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class GameCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new Info(),
            new Join(),
            new Leave(),
            new AddPlayer(),
            new EliminatePlayer(),
            new SetOrder(),
            new Undo(),
            new SCCount(),
            new Setup(),
            new Replace(),
            new SetupGameChannels(),
            new GameEnd(),
            new Ping(),
            new SetUnitCap(),
            new StartPhase(),
            new SetDeck(),
            new CreateGameButton(),
            new WeirdGameSetup(),
            new Swap(),
            new Observer(),
            new Tags(),
            new GameOptions()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.GAME;
    }

    @Override
    public String getDescription() {
        return "Game";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
