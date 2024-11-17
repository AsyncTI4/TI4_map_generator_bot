package ti4.commands.uncategorized;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.player.AbilityInfo;
import ti4.commands.player.UnitInfo;
import ti4.commands.relic.RelicInfo;
import ti4.commands.tech.TechInfo;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.commands2.cardsso.SOInfo;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AllInfo extends GameStateCommand {

    public AllInfo() {
        super(false, true);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text")
                        .setRequired(false),
                new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also")
                        .setRequired(false));
    }

    @Override
    public String getName() {
        return Constants.ALL_INFO;
    }

    @Override
    public String getDescription() {
        return "Send all available info to your Cards Info thread.";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) &&
                CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event) + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        AbilityInfo.sendAbilityInfo(game, player);
        UnitInfo.sendUnitInfo(game, player, false);
        LeaderInfo.sendLeadersInfo(game, player);
        TechInfo.sendTechInfo(game, player);
        RelicInfo.sendRelicInfo(game, player);
        SOInfo.sendSecretObjectiveInfo(game, player);
        ActionCardHelper.sendActionCardInfo(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        CardsInfo.sendVariousAdditionalButtons(game, player);
    }
}
