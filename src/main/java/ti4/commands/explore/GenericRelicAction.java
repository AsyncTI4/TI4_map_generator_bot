package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

abstract class GenericRelicAction extends ExploreSubcommandData {

    public GenericRelicAction(String name, String description) {
        this(name, description, false);
    }

    public GenericRelicAction(String name, String description, boolean noOption) {
        super(name, description);
        if(!noOption) {
            addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit\"").setRequired(false));
        }
    }

    @Override
    final public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getPlayer(activeMap, player, event);
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        doAction(player, event);
    }

    public abstract void doAction(Player player, SlashCommandInteractionEvent event);
}
