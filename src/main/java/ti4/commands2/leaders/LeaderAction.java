package ti4.commands2.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

abstract public class LeaderAction extends GameStateSubcommand {

    public LeaderAction(String id, String description) {
        super(id, description, true, true);
        options();
    }

    protected void options() {
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);

        action(event, leaderID, game, player);
    }

    abstract void action(SlashCommandInteractionEvent event, String leader, Game game, Player player);
}
