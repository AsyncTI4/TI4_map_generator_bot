package ti4.commands.player;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
public class PlanetExhaust extends PlanetAddRemove {
    public PlanetExhaust() {
        super(Constants.PLANET_EXHAUST, "Exhaust Planet");
        addOptions(new OptionData(OptionType.STRING, Constants.SPEND_AS, "Spend the planets as Resources/Influence/Votes/TechSkip or enter your own description").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void doAction(Player player, String planet, Map activeMap) {
        player.exhaustPlanet(planet);
    }
}
