package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SetPlanetTradeGoods extends GameStateSubcommand {

    public SetPlanetTradeGoods() {
        super(Constants.SET_PLANET_TRADEGOODS, "Set tg or comm count on a planet per Harvest or Facility powers", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        int old = getPlayer().getHarvestCounter();
        if (count > 0) {
            getPlayer().setHarvestCounter(count);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set tg on planet count to " + count + " (used to be " + old + ")");
    }
}
