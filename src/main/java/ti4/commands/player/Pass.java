package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Pass extends PlayerSubcommandData {
    public Pass() {
        super(Constants.PASS, "Pass");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.replyToMessage(event, "You're not a player of this game");
            return;
        }
        player.setPassed(true);
        String text = Helper.getPlayerRepresentation(event, player) + " PASSED";
        sendMessage(text);
        Turn turn = new Turn();
        turn.pingNextPlayer(event, activeMap, player);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        MapSaveLoadManager.saveMap(getActiveMap());
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
