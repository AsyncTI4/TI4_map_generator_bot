package ti4.commands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.player.AbilityInfo;
import ti4.commands.player.UnitInfo;
import ti4.commands.relic.RelicInfo;
import ti4.commands.tech.TechInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AllInfo implements Command {

    @Override
    public String getActionID() {
        return Constants.ALL_INFO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game;
        if (!GameManager.doesUserHaveGameContext(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            game = UserGameContextManager.getContextGame(userID);
            String color = Helper.getColor(game, event);
            if (!Mapper.isValidColor(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }
        }

        Player player = game.getPlayer(userID);
        player = Helper.getGamePlayer(game, player, event, null);
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
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false)));
    }

}
