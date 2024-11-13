package ti4.commands.relic;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicInfo extends RelicSubcommandData {
    public RelicInfo() {
        super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendRelicInfo(game, player, event);
    }
    @ButtonHandler(Constants.REFRESH_RELIC_INFO)
    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendRelicInfo(game, player);
    }

    public static void sendRelicInfo(Game game, Player player) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            player.getCardsInfoThread(),
            null,
            getRelicEmbeds(player),
            getRelicButtons(player));
    }

    private static List<MessageEmbed> getRelicEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (String relicID : player.getRelics()) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            if (relicModel != null) {
                MessageEmbed representationEmbed = relicModel.getRepresentationEmbed();
                messageEmbeds.add(representationEmbed);
            }
        }
        return messageEmbeds;

    }

    private static List<Button> getRelicButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_RELIC_INFO);
        return buttons;
    }
}
