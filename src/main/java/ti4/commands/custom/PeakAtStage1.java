package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class PeakAtStage1 extends CustomSubcommandData {
    public PeakAtStage1() {
        super(Constants.PEAK_AT_STAGE1, "Peak at a stage 1 objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location Of Objective (typical 1-5)").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
         Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }
        OptionMapping loc1 = event.getOption(Constants.LOCATION1);
        String obj = activeMap.peakAtStage1(loc1.getAsInt());
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true));
        sb.append(" **Stage 1 Public Objective at location "+loc1.getAsInt()+"**").append("\n");
        sb.append(po.getRepresentation()).append("\n");
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap), sb.toString());
        
    }
}
