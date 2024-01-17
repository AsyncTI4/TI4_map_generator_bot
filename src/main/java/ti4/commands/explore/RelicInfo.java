package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.commands.uncategorized.InfoThreadCommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicInfo extends ExploreSubcommandData implements InfoThreadCommand{
    public RelicInfo() {
		super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel");
	}

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendRelicInfo(activeGame, player, event);
    }

    public static void sendRelicInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendRelicInfo(activeGame, player);
    }

    public static void sendRelicInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                getRelicInfoText(player),
                getRelicButtons(player));
    }

    private static String getRelicInfoText(Player player) {
        List<String> playerRelics = player.getRelics();
        StringBuilder sb = new StringBuilder("__**Relic Info**__\n");
        if (playerRelics == null || playerRelics.isEmpty()) {
            sb.append("> No Relics");
            return sb.toString();
        }
        for (String relicID : playerRelics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            if (relicModel != null) sb.append(Emojis.Relic).append(relicModel.getSimpleRepresentation()).append("\n");
        }
        return sb.toString();
    }

    private static List<Button> getRelicButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.primary(Constants.REFRESH_RELIC_INFO, "Refresh Relic Info"));
        return buttons;
    }
}
