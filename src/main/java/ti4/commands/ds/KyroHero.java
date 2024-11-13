package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.status.ListTurnOrder;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class KyroHero extends GameStateSubcommand {

    public KyroHero() {
        super(Constants.KYRO_HERO, "Mark a strategy card as the target of Speygh, the Kyro Hero.", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy Card Number").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int dieResult = event.getOption(Constants.SC, 1, OptionMapping::getAsInt);
        game.setStoredValue("kyroHeroSC", dieResult + "");
        game.setStoredValue("kyroHeroPlayer", player.getFaction());
        MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getSCName(dieResult, game) + " has been marked with Speygh, the Kyro hero, and the faction that played the hero as " + player.getFaction() + ".");
        ListTurnOrder.turnOrder(event, game);
    }

}
