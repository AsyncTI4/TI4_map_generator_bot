package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SleeperTokenHelper;
import ti4.map.Game;
import ti4.map.Player;

class SleeperToken extends GameStateSubcommand {

    public SleeperToken() {
        super(Constants.SLEEPER_TOKEN, "Select planets were to add/remove Sleeper tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2nd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        sleeperForPlanet(event, game, Constants.PLANET, player);
        sleeperForPlanet(event, game, Constants.PLANET2, player);
        sleeperForPlanet(event, game, Constants.PLANET3, player);
        sleeperForPlanet(event, game, Constants.PLANET4, player);
        sleeperForPlanet(event, game, Constants.PLANET5, player);
        sleeperForPlanet(event, game, Constants.PLANET6, player);
    }

    private void sleeperForPlanet(SlashCommandInteractionEvent event, Game game, String planet, Player player) {
        OptionMapping planetOption = event.getOption(planet);
        if (planetOption == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planetName, player);
    }
}
