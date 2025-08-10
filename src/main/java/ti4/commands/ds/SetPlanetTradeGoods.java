package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

class SetPlanetTradeGoods extends GameStateSubcommand {

    public SetPlanetTradeGoods() {
        super(
                Constants.SET_PLANET_COMMS,
                "Set commodity count (or trade goods) on a planet per Harvest or Facility powers",
                true,
                true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);

        String planet = event.getOption("planet").getAsString();
        int old = 0;
        if (planet.equalsIgnoreCase("uikos")) {
            old = getPlayer().getHarvestCounter();
            if (count > -1) {
                getPlayer().setHarvestCounter(count);
            }
        } else {
            old = getGame().changeCommsOnPlanet(0, planet);
            if (count > -1) {
                getGame().changeCommsOnPlanet(count - old, planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Set commodities on " + Helper.getPlanetRepresentation(planet, getGame()) + " to " + count
                        + " (used to be " + old + ").");
    }
}
