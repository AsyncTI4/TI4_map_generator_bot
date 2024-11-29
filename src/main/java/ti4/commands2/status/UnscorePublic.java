package ti4.commands2.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class UnscorePublic extends GameStateSubcommand {

    public UnscorePublic() {
        super(Constants.UNSCORE_OBJECTIVE, "Unscore Public Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which to score Public Objective"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        var poId = event.getOption(Constants.PO_ID).getAsInt();
        boolean scored = game.unscorePublicObjective(player.getUserID(), poId);
        if (!scored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Public Objective ID found, please retry");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public Objective unscored: " + poId);
        }
    }
}
