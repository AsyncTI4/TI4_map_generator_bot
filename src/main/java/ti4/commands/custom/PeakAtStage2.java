package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class PeakAtStage2 extends CustomSubcommandData {
    public PeakAtStage2() {
        super(Constants.PEAK_AT_STAGE2, "Peak at a stage 2 objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location Of Objective (typical 1-5)").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
         Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }
        OptionMapping loc1 = event.getOption(Constants.LOCATION1);
        secondHalfOfPeak(event, activeGame, player, loc1.getAsInt());
        
    }
    public void secondHalfOfPeak(GenericInteractionCreateEvent event, Game activeGame, Player player, int loc1){
        String obj = activeGame.peakAtStage2(loc1);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
      String sb = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) +
          " **Stage 2 Public Objective at location " + loc1 + "**" + "\n" +
          po.getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), sb);
    }
}
