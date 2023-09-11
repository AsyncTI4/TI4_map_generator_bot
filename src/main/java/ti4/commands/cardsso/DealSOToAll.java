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
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSOToAll extends SOCardsSubcommandData {
    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        dealSOToAll(event, count, activeGame);
    }
    public void dealSOToAll(GenericInteractionCreateEvent event, int count, Game activeGame){
        if(count > 0){
            for (Player player : activeGame.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    activeGame.drawSecretObjective(player.getUserID());
                }
                if(player.hasAbility("plausible_deniability")){
                    activeGame.drawSecretObjective(player.getUserID());
                }
                SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
        if(activeGame.getRound() == 1){
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("startOfGameObjReveal" , "Reveal Objectives and Start Strategy Phase"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), "Press this button after everyone has discarded", buttons);
            Player speaker = null;
            if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
            }
            if (speaker == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Speaker is not yet assigned. Secrets have been dealt, but please assign speaker soon (command is /player stats speaker:y)");
            }
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(Button.success("setOrder" , "Set Speaker Order"));
            buttons2.add(Button.danger("deleteButtons" , "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), Helper.getGamePing(activeGame.getGuild(), activeGame)+ " if your map is not weird (i.e. all players HS are in the same ring on a non-weird map), you can set speaker order using this button", buttons2);
        }
    }
}
