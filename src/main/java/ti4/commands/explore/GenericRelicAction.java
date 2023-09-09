package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

abstract class GenericRelicAction extends ExploreSubcommandData {

    public GenericRelicAction(String name, String description) {
        this(name, description, false);
    }

    public GenericRelicAction(String name, String description, boolean noOption) {
        super(name, description);
        if (!noOption) {
            addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit").setRequired(false));
        }
    }

    @Override
    final public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        doAction(player, event);
    }

    public abstract void doAction(Player player, SlashCommandInteractionEvent event);
}
