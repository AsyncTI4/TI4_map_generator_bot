package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.CommandHelper;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class TechInfo extends TechSubcommandData {
    public TechInfo() {
        super(Constants.INFO, "Send tech information to your Cards Info channel");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendTechInfo(game, player, event);
    }

    @ButtonHandler(Constants.REFRESH_TECH_INFO)
    public static void sendTechInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendTechInfo(game, player);
    }

    public static void sendTechInfo(Game game, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Technologies Researched:**__", getTechMessageEmbeds(player));
        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, player, "_ _\n__**Faction Technologies (Not Yet Researched)**__", getFactionTechMessageEmbeds(player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), null, getTechButtons(player));
    }

    private static List<Button> getTechButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_TECH_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (TechnologyModel techModel : player.getTechs().stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed();
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }

    private static List<MessageEmbed> getFactionTechMessageEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        List<String> notResearchedFactionTechs = player.getNotResearchedFactionTechs();
        for (TechnologyModel techModel : notResearchedFactionTechs.stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed(false, true);
            messageEmbeds.add(representationEmbed);
        }
        return messageEmbeds;
    }
}
