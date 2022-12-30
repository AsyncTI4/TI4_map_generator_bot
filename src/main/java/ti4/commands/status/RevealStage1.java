package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class RevealStage1 extends StatusSubcommandData {
    public RevealStage1() {
        super(Constants.REVEAL_STATGE1, "Reveal Stage1 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        java.util.Map.Entry<String, Integer> objective = activeMap.revealState1();

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(event, activeMap));
        sb.append(" **Stage 1 Public Objective Revealed**").append("\n");
        sb.append("(").append(objective.getValue()).append(") ");
        sb.append(Helper.getEmojiFromDiscord("Public1"));
        sb.append(Mapper.getPublicObjective(objective.getKey())).append("\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
            String userID = event.getUser().getId();
            Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
            MapSaveLoadManager.saveMap(activeMap);
            MessageHelper.replyToMessageTI4Logo(event);
    }
}
