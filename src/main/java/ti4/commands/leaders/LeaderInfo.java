package ti4.commands.leaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class LeaderInfo extends LeaderSubcommandData {
    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread");
    }

    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply();
        Game game = getActiveGame();
        User user = getUser();
        Player player = game.getPlayer(user.getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        sendLeadersInfo(game, player, event);
    }

    @ButtonHandler(Constants.REFRESH_LEADER_INFO)
    public static void sendLeadersInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendLeadersInfo(game, player);
    }

    private static List<Button> getLeaderButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_LEADER_INFO);
        return buttons;
    }

    private static List<MessageEmbed> getPlayersLeaderEmbeds(Player player) {
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Leader leader : player.getLeaders()) {
            embeds.add(leader.getLeaderEmbed());
        }
        return embeds;
    }

    public static void sendLeadersInfo(Game game, Player player) {
        // LEADERS
        List<MessageEmbed> leaderEmbeds = getPlayersLeaderEmbeds(player);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), "**Leaders Information:**", leaderEmbeds, getLeaderButtons(player));

        //PROMISSORY NOTES
        List<MessageEmbed> allianceEmbeds = new ArrayList<>();
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            //PLAY AREA PROMISSORY NOTES
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn.getKey());
                    if (pnModel.getName().equals("Alliance")) {
                        String color = pnModel.getColor().orElse(null);
                        for (Player otherPlayer : game.getPlayers().values()) {
                            if (otherPlayer.getColor().equalsIgnoreCase(color)) {
                                Leader playerLeader = otherPlayer.unsafeGetLeader(Constants.COMMANDER);
                                if (playerLeader == null) {
                                    continue;
                                }
                                allianceEmbeds.add(playerLeader.getLeaderEmbed());
                            }
                        }
                    }
                }
            }
        }
        if (!allianceEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), "**Alliance Commanders:**", allianceEmbeds);
        }

        //ADD YSSARIL AGENT REFERENCE
        List<MessageEmbed> yssarilEmbeds = new ArrayList<>();
        if (player.hasLeader("yssarilagent")) {
            for (Player otherPlayer : game.getPlayers().values()) {
                if (otherPlayer != player) {
                    Leader otherPlayerAgent = otherPlayer.unsafeGetLeader(Constants.AGENT);
                    if (otherPlayerAgent == null) {
                        continue;
                    }
                    yssarilEmbeds.add(otherPlayerAgent.getLeaderEmbed());
                }
            }
        }
        if (!yssarilEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), Emojis.YssarilAgent + "**Clever, Clever Agents:**", yssarilEmbeds);
        }

        //ADD MAHACT IMPERIA REFERENCE
        List<MessageEmbed> imperiaEmbeds = new ArrayList<>();
        if (player.hasAbility("imperia")) {
            for (Player otherPlayer : game.getPlayers().values()) {
                if (otherPlayer != player) {
                    if (player.getMahactCC().contains(otherPlayer.getColor())) {
                        Leader leader = otherPlayer.unsafeGetLeader(Constants.COMMANDER);
                        if (leader == null) {
                            continue;
                        }
                        imperiaEmbeds.add(leader.getLeaderEmbed());
                    }
                }
            }
        }
        if (!imperiaEmbeds.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), "**Commanders from " + Emojis.Mahact + " Imperia:**", imperiaEmbeds);
        }
    }
}
