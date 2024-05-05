package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendTechInfo(activeGame, player, event);
    }

    public static void sendTechInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendTechInfo(activeGame, player);
    }

    public static void sendTechInfo(Game activeGame, Player player) {
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "_ _\n__**Technologies Researched:**__", getTechMessageEmbeds(player));
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "_ _\n__**Faction Technologies (Not Yet Researched)**__", getFactionTechMessageEmbeds(player));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), null, getTechButtons(player)); 
        ButtonHelper.getUnresearchedTech(player);
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

    public static void sendNotResearchedTechInfo(Game activeGame, Player player, String type) {
        // Get the list of not researched propulsion technologies for the player
        List<String> propulsion = player.getTechnologyTypeMap(false).getOrDefault(type, List.of());

        // Create a list to hold message embeds
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        
        // Iterate over each propulsion technology, retrieve its model, sort by requirements, and add to the list of message embeds
        for (TechnologyModel techModel : propulsion.stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
            // Generate a representation embed for the technology model and add it to the list of message embeds
            MessageEmbed representationEmbed = techModel.getRepresentationEmbed(false,true);
            messageEmbeds.add(representationEmbed);
        }

        // Send the message embeds to the player's cards info thread
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, null, messageEmbeds);
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
