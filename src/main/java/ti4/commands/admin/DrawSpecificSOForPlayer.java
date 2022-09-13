package ti4.commands.admin;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;
import java.util.List;

public class DrawSpecificSOForPlayer extends AdminSubcommandData {

    public DrawSpecificSOForPlayer() {
        super(Constants.DRAW_SPECIFIC_SO_FOR_PLAYER, "Draw specific SO for player");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "SO ID").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do draw SO").setRequired(true));
    }


    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping option = event.getOption(Constants.SO_ID);
        if (option == null) {
            MessageHelper.replyToMessage(event, "SO ID needs to be specified");
            return;
        }
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            LinkedHashMap<String, Integer> secrets = activeMap.drawSpecificSecretObjective(option.toString(), user.getId());
            if (secrets == null){
                MessageHelper.replyToMessage(event, "SO not retrieved");
                return;
            }
            MapSaveLoadManager.saveMap(activeMap);
            return;
        }
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
