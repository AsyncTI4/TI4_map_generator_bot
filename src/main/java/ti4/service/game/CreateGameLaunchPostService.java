package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.helpers.ButtonHelper;

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
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("joinGameList", "Join Game"));
        buttons.add(Buttons.red("leaveGameList", "Leave Game"));
        buttons.add(Buttons.gray("editPlayers~MDL", "Add Players"));
        buttons.add(Buttons.gray("removePlayers~MDL", "Remove Players"));
        buttons.add(Buttons.gray("addSillyName~MDL", "Add Fun Game Name"));
        buttons.add(Buttons.blue("launchGame", "Launch Game"));

        String message =
                CREATE_GAME_FLOW_MESSAGE + CreateGameButtonHandler.generateMemberListMessage(members, gameFunName);
        channel.sendMessage(message)
                .addComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queueAfter(2, TimeUnit.SECONDS);
    }
}
