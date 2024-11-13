package ti4.commands.relic;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicSend extends RelicSubcommandData {
    public RelicSend() {
        super(Constants.RELIC_SEND, "Send a relic to another Player");
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to send from Target to Source").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target Faction or Color").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source Faction or Color (default is you)").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player1 = CommandHelper.getPlayerFromEvent(game, event);
        if (player1 == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        //resolve player2
        Player player2 = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (player2 == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player 2 could not be found");
            return;
        }
        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The two players provided are the same player");
            return;
        }

        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        List<String> player1Relics = player1.getRelics();
        if (!player1Relics.contains(relicID)) {
            MessageHelper.sendMessageToEventChannel(event, player1.getUserName() + " does not have relic: " + relicID);
            return;
        }

        player1.removeRelic(relicID);
        player2.addRelic(relicID);

        //HANDLE SHARD OF THE THRONE
        String shardCustomPOName = null;
        Integer shardPublicObjectiveID = null;
        switch (relicID) {
            case "shard" -> {
                shardCustomPOName = "Shard of the Throne";
                shardPublicObjectiveID = game.getRevealedPublicObjectives().get("Shard of the Throne");
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                shardCustomPOName = "Shard of the Throne (" + absolShardNum + ")";
                shardPublicObjectiveID = game.getRevealedPublicObjectives().get(shardCustomPOName);
            }
        }
        if (shardCustomPOName != null && shardPublicObjectiveID != null && game.getCustomPublicVP().containsKey(shardCustomPOName) && game.getCustomPublicVP().containsValue(shardPublicObjectiveID)) {
            game.unscorePublicObjective(player1.getUserID(), shardPublicObjectiveID);
            game.scorePublicObjective(player2.getUserID(), shardPublicObjectiveID);
        }

        if (player1.hasRelic(relicID) || !player2.hasRelic(relicID)) {
            MessageHelper.sendMessageToEventChannel(event, "Something may have gone wrong - please check your relics and ping Bothelper if there is a problem.");
            return;
        }
        RelicModel relicModel = Mapper.getRelic(relicID);
        String sb = player1.getRepresentation() +
            " sent a relic to " + player2.getRepresentation() +
            "\n" + relicModel.getSimpleRepresentation();
        MessageHelper.sendMessageToEventChannel(event, sb);
        Helper.checkEndGame(game, player2);
    }
}
