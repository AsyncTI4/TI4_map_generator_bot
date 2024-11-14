package ti4.commands.uncategorized;

import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.player.AbilityInfo;
import ti4.commands.player.UnitInfo;
import ti4.commands.relic.RelicInfo;
import ti4.commands.tech.TechInfo;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AllInfo implements Command {

    @Override
    public String getName() {
        return Constants.ALL_INFO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getName());
    }

    public static boolean acceptEvent(SlashCommandInteractionEvent event, String actionID) {
        if (event.getName().equals(actionID)) {
            String userID = event.getUser().getId();
            if (!GameManager.isUserWithActiveGame(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Member member = event.getMember();
            if (member != null) {
                List<Role> roles = member.getRoles();
                for (Role role : AsyncTI4DiscordBot.adminRoles) {
                    if (roles.contains(role)) {
                        return true;
                    }
                }
            }
            Game userActiveGame = GameManager.getUserActiveGame(userID);
            if (userActiveGame.isCommunityMode()) {
                Player player = CommandHelper.getPlayerFromEvent(userActiveGame, event);
                if (player == null || !userActiveGame.getPlayerIDs().contains(player.getUserID()) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                    MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                    return false;
                }
            } else if (!userActiveGame.getPlayerIDs().contains(userID) && !event.getUser().getId().equals(AsyncTI4DiscordBot.userID)) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            if (!event.getChannel().getName().startsWith(userActiveGame.getName() + "-")) {
                MessageHelper.replyToMessage(event, "Commands may be executed only in game specific channels.");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game;
        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            game = GameManager.getUserActiveGame(userID);
            String color = CommandHelper.getColor(game, event);
            if (!Mapper.isValidColor(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }
        }

        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event) + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        AbilityInfo.sendAbilityInfo(game, player);
        UnitInfo.sendUnitInfo(game, player, false);
        LeaderInfo.sendLeadersInfo(game, player);
        TechInfo.sendTechInfo(game, player);
        RelicInfo.sendRelicInfo(game, player);
        SOInfo.sendSecretObjectiveInfo(game, player);
        ACInfo.sendActionCardInfo(game, player);
        PNInfo.sendPromissoryNoteInfo(game, player, false);
        CardsInfo.sendVariousAdditionalButtons(game, player);
    }

    protected String getActionDescription() {
        return "Send all available info to your Cards Info thread.";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false)));
    }

}
