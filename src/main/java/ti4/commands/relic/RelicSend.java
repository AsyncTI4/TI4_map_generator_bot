package ti4.commands.relic;

import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
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
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_2, "Target Faction or Color").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source Faction or Color (default is you)").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player1 = game.getPlayer(getUser().getId());
        player1 = Helper.getGamePlayer(game, player1, event, null);
        player1 = Helper.getPlayerFromEvent(game, player1, event);
        if (player1 == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        String targetFaction = event.getOption(Constants.FACTION_COLOR_2, null, OptionMapping::getAsString);
        String sourceFaction = event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString);

        //resolve player1
        if (sourceFaction != null) {
            String factionColor = AliasHandler.resolveColor(sourceFaction.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player1 = player_;
                    break;
                }
            }
        }

        //resolve player2
        Player player2 = null; //Player to send to
        if (targetFaction != null) {
            String factionColor = AliasHandler.resolveColor(targetFaction.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player2 = player_;
                    break;
                }
            }
        }
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The two players provided are the same player");
            return;
        }

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
