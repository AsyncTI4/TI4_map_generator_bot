package ti4.commands.ds;

import java.util.Collection;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class TrapReveal extends GameStateSubcommand {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        Player player = getPlayer();
        Collection<Integer> values = player.getTrapCards().values();
        int trapID = event.getOption(Constants.LIZHO_TRAP_ID).getAsInt();
        if (!values.contains(trapID)) {
            MessageHelper.replyToMessage(event, "Trap ID not found");
            return;
        }
        String stringTrapID = "";
        for (String trapIDS : player.getTrapCards().keySet()) {
            if (player.getTrapCards().get(trapIDS) == trapID) {
                stringTrapID = trapIDS;
            }
        }
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planetName, stringTrapID, player, true);
    }
}
