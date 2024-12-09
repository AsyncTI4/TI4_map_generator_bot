package ti4.commands2.game;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.game.SwapFactionService;

class Swap extends GameStateSubcommand {

    public Swap() {
        super(Constants.SWAP, "Swap factions with a player", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Swap with player in Faction/Color ").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.TARGET_PLAYER, "Replacement player @playerName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User callerUser = event.getUser();
        Game game = getGame();
        Collection<Player> players = game.getPlayers().values();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.adminRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (players.stream().noneMatch(player -> player.getUserID().equals(callerUser.getId())) && !isAdmin) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only game players can swap with a player.");
            return;
        }
        String message = "";
        OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);
        OptionMapping addOption = event.getOption(Constants.TARGET_PLAYER);
        if (removeOption != null && addOption != null) {
            Player removedPlayer = CommandHelper.getPlayerFromEvent(game, event);
            Player swapperPlayer = game.getPlayer(addOption.getAsUser().getId());
            if (removedPlayer == null) {
                MessageHelper.replyToMessage(event, "Could not find player for faction/color to replace");
                return;
            }
            if (swapperPlayer == null || swapperPlayer.getFaction() == null) {
                MessageHelper.replyToMessage(event, "Could not find faction/player to swap");
                return;
            }
            User addedUser = addOption.getAsUser();
            SwapFactionService.secondHalfOfSwap(game, swapperPlayer, removedPlayer, addedUser, event);
        } else {
            MessageHelper.replyToMessage(event, "Specify player to swap");
            return;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }
}
