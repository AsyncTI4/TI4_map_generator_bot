package ti4.discord.interactions.commands.game;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class GameCommand extends AbstractParentCommand {

    public GameCommand() {
        super(
                Constants.GAME,
                "Game",
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
                new GalacticEventsSetup(),
                new GameOptions(),
                new StartScenario(),
                new PlaceDrawableCard());
    }
}
