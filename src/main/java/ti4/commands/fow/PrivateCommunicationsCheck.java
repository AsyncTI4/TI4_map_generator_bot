package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;

class PrivateCommunicationsCheck extends GameStateSubcommand {

    public PrivateCommunicationsCheck() {
        super(Constants.CHECK_PRIVATE_COMMUNICATIONS, "Check the status of private communication threads and offer a button to suggest new ones.", false, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which check as", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        Game game = getGame();

        if (!FowCommunicationThreadService.isActive(game)) {
            MessageHelper.replyToMessage(event, "Bot managed communication threads are not enabled.\nEnable them with `/fow fow_options`");
            return;
        }

        Player commandUser = game.getPlayer(event.getUser().getId());
        if (commandUser == null) {
            MessageHelper.replyToMessage(event, "You are not a player in this game.");
            return;
        }

        boolean teammate = false;
        for (Player p : game.getRealPlayers()) {
            if (p.getTeamMateIDs().contains(commandUser.getUserID())) {
                player = p;
                teammate = true;
                break;
            }
        }

        if (player != commandUser && !game.getPlayersWithGMRole().contains(commandUser) && !teammate) {
            MessageHelper.replyToMessage(event, "Only GM can use this for another player.");
            return;
        }

        FowCommunicationThreadService.checkNewNeighbors(game, player);
        MessageHelper.replyToMessage(event, "Communication threads with neighbors checked.");
    }
}