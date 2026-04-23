package ti4.discord.interactions.slashcommands.uncategorized;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.CommandHelper;
import ti4.discord.interactions.slashcommands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
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
        return "Send all available information to your #cards-info thread";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event) + ".";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        AbilityInfoService.sendAbilityInfo(player);
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
