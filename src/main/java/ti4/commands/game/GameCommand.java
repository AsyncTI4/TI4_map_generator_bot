package ti4.commands.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.buttons.Buttons;
import ti4.commands.ParentCommand;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class GameCommand implements ParentCommand {

    private final Collection<GameSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.GAME;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean undoCommand = false;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (GameSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                if (subcommandName.equals(Constants.UNDO)) {
                    undoCommand = true;
                }
            }
        }
        String userID = event.getUser().getId();
        Game game = CommandHelper.getGameName(event);
        if (game == null) return;
        if (!undoCommand) {
            GameSaveLoadManager.saveGame(game, event);
        }

        // Post Map Image Unless Command is x
        if (!Constants.GAME_END.equalsIgnoreCase(subcommandName) &&
            !Constants.PING.equalsIgnoreCase(subcommandName) &&
            !Constants.SET_DECK.equalsIgnoreCase(subcommandName) &&
            !Constants.CREATE_GAME_BUTTON.equalsIgnoreCase(subcommandName) &&
            !Constants.OBSERVER.equalsIgnoreCase(subcommandName) &&
            !Constants.OPTIONS.equalsIgnoreCase(subcommandName)) {

            MapRenderPipeline.render(game, event, fileUpload -> {
                List<Button> buttons = new ArrayList<>();
                if (!game.isFowMode()) {
                    Button linkToWebsite = Button.link("https://ti4.westaddisonheavyindustries.com/game/" + game.getName(), "Website View");
                    buttons.add(linkToWebsite);
                    buttons.add(Buttons.green("gameInfoButtons", "Player Info"));
                }
                buttons.add(Buttons.green("cardsInfo", "Cards Info"));
                buttons.add(Buttons.blue("offerDeckButtons", "Show Decks"));
                buttons.add(Buttons.gray("showGameAgain", "Show Game (Refresh Map)"));
                MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), fileUpload, "", buttons);
            });
        }
    }

    public String getDescription() {
        return "Game";
    }

    private Collection<GameSubcommandData> getSubcommands() {
        Collection<GameSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new Info());
        subcommands.add(new Join());
        subcommands.add(new Leave());
        subcommands.add(new Add());
        subcommands.add(new Eliminate());
        subcommands.add(new SetOrder());
        subcommands.add(new Undo());
        subcommands.add(new SCCount());
        subcommands.add(new Setup());
        subcommands.add(new Replace());
        subcommands.add(new SetupGameChannels());
        subcommands.add(new GameEnd());
        subcommands.add(new Ping());
        subcommands.add(new SetUnitCap());
        subcommands.add(new StartPhase());
        subcommands.add(new SetDeck());
        //subcommands.add(new GameCreate());
        subcommands.add(new CreateGameButton());
        subcommands.add(new WeirdGameSetup());
        subcommands.add(new Swap());
        subcommands.add(new Observer());
        subcommands.add(new Tags());
        subcommands.add(new GameOptions());
        // subcommands.add(new ReverseSpeakerOrder());
        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getDescription())
                .addSubcommands(getSubcommands()));
    }
}
