package ti4.commands2.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.AbilityInfoService;
import ti4.service.info.CardsInfoService;
import ti4.service.info.LeaderInfoService;
import ti4.service.info.RelicInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.info.TechInfoService;
import ti4.service.info.UnitInfoService;

public class AllInfoCommand extends GameStateCommand {

    public AllInfoCommand() {
        super(false, true);
    }

    @Override
    public String getName() {
        return Constants.ALL_INFO;
    }

    @Override
    public String getDescription() {
        return "Send all available infomation to your #cards-info thread";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event) + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        AbilityInfoService.sendAbilityInfo(game, player);
        UnitInfoService.sendUnitInfo(player, false);
        LeaderInfoService.sendLeadersInfo(game, player);
        TechInfoService.sendTechInfo(player);
        RelicInfoService.sendRelicInfo(player);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        ActionCardHelper.sendActionCardInfo(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        CardsInfoService.sendVariousAdditionalButtons(game, player);
    }
}
