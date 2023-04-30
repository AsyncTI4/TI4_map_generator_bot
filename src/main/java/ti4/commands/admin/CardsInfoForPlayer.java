package ti4.commands.admin;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;


public class CardsInfoForPlayer extends AdminSubcommandData {
    public CardsInfoForPlayer() {
        super(Constants.INFO, "Resent all my cards in Private Message");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to which to show Action Card").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            Player player = activeMap.getPlayer(user.getId());
            ACInfo_Legacy.sentUserCardInfo(event, activeMap, player);
        }
        MapSaveLoadManager.saveMap(activeMap, event);
        sendMessage("Info sent");
    }
}
