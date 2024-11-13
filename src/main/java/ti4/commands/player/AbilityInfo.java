package ti4.commands.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;

public class AbilityInfo extends PlayerSubcommandData {
    public AbilityInfo() {
        super(Constants.ABILITY_INFO, "Send faction abilities information to your Cards Info channel");
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
        sendAbilityInfo(game, player, event);
    }

    @ButtonHandler("refreshAbilityInfo")
    public static void sendAbilityInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendAbilityInfo(game, player);
    }

    public static void sendAbilityInfo(Game game, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Abilities:**__", getAbilityMessageEmbeds(player));
    }

    private static List<MessageEmbed> getAbilityMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (AbilityModel model : player.getAbilities().stream().map(Mapper::getAbility).sorted(Comparator.comparing(AbilityModel::getAlias)).toList()) {
            MessageEmbed representationEmbed = model.getRepresentationEmbed();
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
