package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.status.ListTurnOrder;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class KyroHero extends DiscordantStarsSubcommandData {

    public KyroHero() {
        super(Constants.KYRO_HERO, "Mark an SC as the target of Kyro Hero");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "SC #").setRequired(true));
        // addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_ALL_ASYNC_TILES, "True to include all async blue back tiles in this list (not just PoK + DS). Default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        int dieResult = event.getOption(Constants.SC, 1, OptionMapping::getAsInt);
        activeGame.setStoredValue("kyroHeroSC", dieResult+"");
        activeGame.setStoredValue("kyroHeroPlayer", player.getFaction());
        MessageHelper.sendMessageToChannel(event.getChannel(), "Marked the Blex Hero Target as SC #"+dieResult + " and the faction that played the hero as "+player.getFaction());
        ListTurnOrder.turnOrder(event, activeGame);
    }
    
}
