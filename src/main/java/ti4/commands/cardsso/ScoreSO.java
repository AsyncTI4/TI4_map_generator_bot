package ti4.commands.cardsso;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScoreSO extends SOCardsSubcommandData {
    public ScoreSO() {
        super(Constants.SCORE_SO, "Score Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select which Secret Objective to score");
            return;
        }

        int soID = option.getAsInt();
        scoreSO(event, game, player, soID, event.getChannel());
    }

    public static void scoreSO(GenericInteractionCreateEvent event, Game game, Player player, int soID, MessageChannel channel) {
        Set<String> alreadyScoredSO = new HashSet<>(player.getSecretsScored().keySet());
        boolean scored = game.scoreSecretObjective(player.getUserID(), soID);
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel, "No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder message = new StringBuilder(player.getRepresentation() + " scored " + Emojis.SecretObjectiveAlt + " ");
        for (Map.Entry<String, Integer> entry : player.getSecretsScored().entrySet()) {
            if (alreadyScoredSO.contains(entry.getKey())) {
                continue;
            }
            message.append(SOInfo.getSecretObjectiveRepresentation(entry.getKey())).append("\n");
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.hasLeaderUnlocked("tnelishero")) {
                    List<Button> buttons = new ArrayList<>();
                    String soStringID = entry.getKey();
                    buttons.add(Button.success("tnelisHeroAttach_" + soStringID, "Attach to " + Mapper.getSecretObjectivesJustNames().get(soStringID)));
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    String msg = p2.getRepresentation(true, true) + " you have the opportunity to attach your hero to the recently scored SO " + Mapper.getSecretObjectivesJustNames().get(soStringID) + ". Use buttons to resolve";
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), msg, buttons);
                }
            }
            if (entry.getKey().equalsIgnoreCase("dhw")) {
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() == 2) {
                    List<String> fragmentsToPurge = new ArrayList<>();
                    List<String> playerFragments = player.getFragments();
                    fragmentsToPurge.addAll(playerFragments);
                    for (String fragid : fragmentsToPurge) {
                        player.removeFragment(fragid);
                        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                    }
                    String message2 = player.getRepresentation() + " purged fragments: "
                        + fragmentsToPurge;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                } else {
                    Player p1 = player;
                    String finChecker = p1.getFinsFactionCheckerPrefix();
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Button.primary(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Button.success(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Button.danger(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
                        Button transact = Button.secondary(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Button.success(finChecker + "deleteButtons", "Done purging");
                    purgeFragButtons.add(transact2);

                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Purge 2 fragments please", purgeFragButtons);
                }
            }
        }
        if (event != null && channel.getName().equalsIgnoreCase(event.getChannel().getName())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        } else {
            MessageHelper.sendMessageToChannel(channel, message.toString());
        }

        // FoW logic, specific for players with visilibty, generic for the rest
        if (game.isFoWMode()) {
            FoWHelper.pingPlayersDifferentMessages(game, event, player, message.toString(), "Scores changed");
            MessageHelper.sendMessageToChannel(channel, "All players notified");
        }
        String headerText = player.getRepresentation();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        SOInfo.sendSecretObjectiveInfo(game, player);
        Helper.checkIfHeroUnlocked(event, game, player);
        if (player.getLeaderIDs().contains("nomadcommander") && !player.hasLeaderUnlocked("nomadcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "nomad", event);
        }
        Helper.checkEndGame(game, player);
    }
}
