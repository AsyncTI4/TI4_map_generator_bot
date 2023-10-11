package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
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
            sendMessage("Player could not be found");
            return;
        }
        sendTechInfo(activeGame, player, event);
    }

    public static void sendTechInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendTechInfo(activeGame, player);
    }

    public static void sendTechInfo(Game activeGame, Player player) {
        //TECH INFO
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "_ _\n__**Technologies Researched:**__", getTechMessageEmbeds(player));
        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "_ _\n__**Faction Technologies (Not Yet Researched)**__", getFactionTechMessageEmbeds(player));

        //BUTTONS
        String exhaustTechMsg = "_ _\nClick a button below to exhaust a Technology:";
        List<Button> techButtons = getTechButtons(activeGame, player);
        if (techButtons != null && !techButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(exhaustTechMsg, techButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread();
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    }

    private static List<Button> getTechButtons(Game activeGame, Player player) {
        return null;
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
        FactionModel factionModel = Mapper.getFactionSetup(player.getFaction());
        if (factionModel != null) {
            List<String> notResearchedFactionTechs = factionModel.getFactionTech().stream().filter(techID -> !player.getTechs().contains(techID)).toList();
            for (TechnologyModel techModel : notResearchedFactionTechs.stream().map(Mapper::getTech).sorted(TechnologyModel.sortByTechRequirements).toList()) {
                MessageEmbed representationEmbed = techModel.getRepresentationEmbed(false, true);
                messageEmbeds.add(representationEmbed);
            }
        }
        return messageEmbeds;
    }
}
