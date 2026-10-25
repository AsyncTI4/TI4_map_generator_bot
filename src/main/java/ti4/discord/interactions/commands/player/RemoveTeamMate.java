package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.TeamMateAccessService;

class RemoveTeamMate extends GameStateSubcommand {

    public RemoveTeamMate() {
        super(Constants.REMOVE_TEAMMATE, "Remove a teammate", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        User targetPlayer = event.getOption(Constants.PLAYER2).getAsUser();
        boolean wasOnTeam = player.getTeamMateIDs().contains(targetPlayer.getId());
        player.removeTeamMateID(targetPlayer.getId());
        if (wasOnTeam) {
            TeamMateAccessService.revokeAccess(game, player, targetPlayer.getId());
        }
        String message = "Removed " + targetPlayer.getAsMention() + " from " + player.getFaction() + "'s team";
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
