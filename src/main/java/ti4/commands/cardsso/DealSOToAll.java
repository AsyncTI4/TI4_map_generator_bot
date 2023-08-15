package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSOToAll extends SOCardsSubcommandData {
    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        dealSOToAll(event, count, activeMap);
    }
    public void dealSOToAll(GenericInteractionCreateEvent event, int count, Map activeMap){
        if(count > 0){
            for (Player player : activeMap.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    activeMap.drawSecretObjective(player.getUserID());
                }
                if(player.hasAbility("plausible_deniability")){
                    activeMap.drawSecretObjective(player.getUserID());
                }
                SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
        if(activeMap.getRound() == 1){
            List<Button> buttons = new ArrayList<Button>();
            buttons.add(Button.success("startOfGameObjReveal" , "Reveal Objectives and Start Strategy Phase"));
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), "Press this button after everyone has discarded", buttons);
        }
    }
}
