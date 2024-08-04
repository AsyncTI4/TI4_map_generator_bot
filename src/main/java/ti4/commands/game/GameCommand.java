package ti4.commands.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.Command;
import ti4.generator.MapGenerator;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class GameCommand implements Command {

    private final Collection<GameSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
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
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        if (game == null)
            return;
        if (!undoCommand) {
            GameSaveLoadManager.saveMap(game, event);
        }
        CompletableFuture<FileUpload> fileFuture = MapGenerator.saveImage(game, event);
        if (!Constants.GAME_END.equalsIgnoreCase(subcommandName) && !Constants.PING.equalsIgnoreCase(subcommandName)
            && !Constants.SET_DECK.equalsIgnoreCase(subcommandName)
            && !Constants.CREATE_GAME_BUTTON.equalsIgnoreCase(subcommandName)
            && !Constants.OBSERVER.equalsIgnoreCase(subcommandName)) {
            fileFuture.thenAccept(fileUpload -> {
                List<Button> buttons = new ArrayList<>();
                if (!game.isFowMode()) {
                    Button linkToWebsite = Button.link(
                        "https://ti4.westaddisonheavyindustries.com/game/" + game.getName(), "Website View");
                    buttons.add(linkToWebsite);
                    buttons.add(Button.success("gameInfoButtons", "Player Info"));
                }
                buttons.add(Button.success("cardsInfo", "Cards Info"));
                buttons.add(Button.primary("offerDeckButtons", "Show Decks"));
                buttons.add(Button.secondary("showGameAgain", "Show Game"));
                MessageHelper.sendFileToChannelWithButtonsAfter(event.getMessageChannel(), fileUpload, "", buttons);
            });
        }
    }

    protected String getActionDescription() {
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
        // subcommands.add(new GameCreate());
        subcommands.add(new CreateGameButton());
        subcommands.add(new WeirdGameSetup());
        subcommands.add(new Swap());
        subcommands.add(new Observer());
        subcommands.add(new Tags());
        // subcommands.add(new ReverseSpeakerOrder());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
