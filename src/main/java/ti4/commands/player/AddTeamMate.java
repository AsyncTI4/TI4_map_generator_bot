package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class AddTeamMate extends GameStateSubcommand {

    public AddTeamMate() {
        super(Constants.ADD_TEAMMATE, "Add a teammate", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to add the user to").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        User player2 = event.getOption(Constants.PLAYER2).getAsUser();
        if (player.getTeamMateIDs().contains(player2.getId())) {
            MessageHelper.sendMessageToEventChannel(event, "User " + player2.getAsMention() + " is already a part of " + player.getFaction() + "'s team.");
            return;
        }
        player.addTeamMateID(player2.getId());

        game.setCommunityMode(true);
        MessageHelper.sendMessageToEventChannel(event, "Added " + player2.getAsMention() + " as part of " + player.getFaction() + "'s team.");
    }
}
