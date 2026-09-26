package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.logging.BotLogger;

@UtilityClass
public class CreateGameLaunchPostService {

    public static final String MAKING_NEW_GAMES_CHANNEL = "making-new-games";
    public static final String MAKING_PRIVATE_GAMES_CHANNEL = "making-private-games";
    public static final String MAKING_TIGL_GAMES_CHANNEL = "making-tigl-games";
    public static final String MAKING_SUPERFAST_GAMES_CHANNEL = "making-superfast-games";

    private static final String CREATE_GAME_FLOW_MESSAGE = """
        To launch a new game, please use the buttons. Players can add themselves or you can add them manually. Once all players are added, press the launch button.
        """;

    public static boolean isCreateGameLaunchParentName(String parentName) {
        return MAKING_NEW_GAMES_CHANNEL.equalsIgnoreCase(parentName)
                || MAKING_PRIVATE_GAMES_CHANNEL.equalsIgnoreCase(parentName)
                || MAKING_TIGL_GAMES_CHANNEL.equalsIgnoreCase(parentName)
                || MAKING_SUPERFAST_GAMES_CHANNEL.equalsIgnoreCase(parentName);
    }

    public static void postLaunchButtons(ThreadChannel channel, List<Member> members, String gameFunName) {
        postLaunchButtons(channel, members, gameFunName, Consumers.nop());
    }

    public static void postLaunchButtons(
            ThreadChannel channel, List<Member> members, String gameFunName, Consumer<Message> onPosted) {
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Buttons.green("joinGameList", "Join Game"),
                Buttons.red("leaveGameList", "Leave Game"),
                Buttons.gray("editPlayers~MDL", "Add Players"),
                Buttons.gray("removePlayers~MDL", "Remove Players"),
                Buttons.gray("addSillyName~MDL", "Set Game Name")));
        String parentName = channel.getParentChannel().getName();
        if (MAKING_NEW_GAMES_CHANNEL.equalsIgnoreCase(parentName)
                || MAKING_TIGL_GAMES_CHANNEL.equalsIgnoreCase(parentName)) {
            rows.add(ActionRow.of(
                    Buttons.green("searchForPlayers~MDL", "Join Matchmaking"),
                    Buttons.red("leaveMatchmaking", "Leave Matchmaking")));
        }
        rows.add(ActionRow.of(Buttons.blue("launchGame", "Launch Game")));

        String message =
                CREATE_GAME_FLOW_MESSAGE + CreateGameButtonHandler.generateMemberListMessage(members, gameFunName);
        channel.sendMessage(message)
                .addComponents(rows)
                .queueAfter(2, TimeUnit.SECONDS, onPosted, BotLogger::catchRestError);
    }
}
