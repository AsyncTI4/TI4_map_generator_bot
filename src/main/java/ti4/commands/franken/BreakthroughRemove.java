package ti4.commands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;

class BreakthroughRemove extends GameStateSubcommand {

    public BreakthroughRemove() {
        super(Constants.BREAKTHROUGH_REMOVE, "Set the breakthrough you are using", true, true);
        addOption(OptionType.STRING, Constants.BREAKTHROUGH, "Which breakthrough to remove", true, true);
        addOption(OptionType.STRING, Constants.FACTION_COLOR, "Faction to remove the breakthrough", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String btID = event.getOption(Constants.BREAKTHROUGH, null, OptionMapping::getAsString);
        List<String> bts = BreakthroughCommandHelper.getBreakthroughsFromEvent(event, null);
        if (!bts.isEmpty()) {
            btID = bts.getFirst();
        }
        if (!Mapper.isValidBreakthrough(btID)) {
            MessageHelper.replyToMessage(event, "Could not find breakthrough with ID: `" + btID + "`");
        }

        player.removeBreakthrough(btID);
        String msg = player.getFactionEmojiOrColor() + " breakthrough removed: `";
        msg += Mapper.getBreakthrough(btID).getName() + "`";
        MessageHelper.sendMessageToEventChannel(event, msg);
    }
}
