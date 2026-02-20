package ti4.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

class DrawRandomParadigm extends GameStateSubcommand {

    DrawRandomParadigm() {
        super("draw_random_paradigm", "Draw a random Twilight's Fall Paradigm", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you draw")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        ButtonHelperTwilightsFall.drawParadigm(game, player, null, false);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
