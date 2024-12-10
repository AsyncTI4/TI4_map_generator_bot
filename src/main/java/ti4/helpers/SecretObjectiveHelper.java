package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.HeroUnlockCheckService;

public class SecretObjectiveHelper {

    public static void scoreSO(GenericInteractionCreateEvent event, Game game, Player player, int soID, MessageChannel channel) {
        Set<String> alreadyScoredSO = new HashSet<>(player.getSecretsScored().keySet());
        boolean scored = game.scoreSecretObjective(player.getUserID(), soID);
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel, "No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder message = new StringBuilder(player.getRepresentation() + " scored " + CardEmojis.SecretObjectiveAlt + " ");
        for (Map.Entry<String, Integer> entry : player.getSecretsScored().entrySet()) {
            if (alreadyScoredSO.contains(entry.getKey())) {
                continue;
            }
            if (ListPlayerInfoService.getObjectiveThreshold(entry.getKey(), game) > 0) {
                message.append(SecretObjectiveInfoService.getSecretObjectiveRepresentationNoNewLine(entry.getKey()));
                message.append(" (").append(ListPlayerInfoService.getPlayerProgressOnObjective(entry.getKey(), game, player)).append("/").append(ListPlayerInfoService.getObjectiveThreshold(entry.getKey(), game)).append(")\n");
            } else {
                message.append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(entry.getKey()));
            }
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.hasLeaderUnlocked("tnelishero")) {
                    List<Button> buttons = new ArrayList<>();
                    String soStringID = entry.getKey();
                    buttons.add(Buttons.green("tnelisHeroAttach_" + soStringID, "Attach to " + Mapper.getSecretObjectivesJustNames().get(soStringID)));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = p2.getRepresentationUnfogged() + " you have the opportunity to attach Turra Sveyar, the Tnelis hero, to the recently scored SO " + Mapper.getSecretObjectivesJustNames().get(soStringID) + ". Use buttons to resolve.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
            }
            if (entry.getKey().equalsIgnoreCase("dhw")) {
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() == 2) {
                    List<String> playerFragments = player.getFragments();
                    List<String> fragmentsToPurge = new ArrayList<>(playerFragments);
                    for (String fragid : fragmentsToPurge) {
                        player.removeFragment(fragid);
                        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                    }

                    CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");

                    String message2 = player.getRepresentation() + " purged fragments: " + fragmentsToPurge;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                } else {
                    String finChecker = player.getFinsFactionCheckerPrefix();
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (player.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (player.getUrf() > 0) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_1",
                            "Purge 1 Frontier Fragment");
                        purgeFragButtons.add(transact);
                    }
                    Button transact2 = Buttons.green(finChecker + "deleteButtons", "Done purging");
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
        if (game.isFowMode()) {
            FoWHelper.pingPlayersDifferentMessages(game, event, player, message.toString(), "Scores changed");
            MessageHelper.sendMessageToChannel(channel, "All players notified");
        }
        String headerText = player.getRepresentation();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        HeroUnlockCheckService.checkIfHeroUnlocked(game, player);
        CommanderUnlockCheckService.checkPlayer(player, "nomad");
        Helper.checkEndGame(game, player);
    }

    public static void showAll(Player player, Player player_, Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Secret Objectives:").append("\n");
        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        Collections.shuffle(secrets);
        for (String id : secrets) {
            sb.append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(id)).append("\n");
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, game, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "All SOs shown to player");
    }

    public static List<Button> getUnscoredSecretObjectiveButtons(Player player) {
        Map<String, Integer> secretObjectives = player.getSecrets();
        List<Button> soButtons = new ArrayList<>();
        if (secretObjectives != null && !secretObjectives.isEmpty()) {
            for (Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Buttons.blue(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName, CardEmojis.SecretObjective));
                }
            }
        }
        return soButtons;
    }

    public static void sendSODiscardButtons(Player player) {
        sendSODiscardButtons(player, "");
    }

    public static void sendSODiscardButtons(Player player, String suffix) {
        List<Button> buttons = getSODiscardButtonsWithSuffix(player, suffix);
        String message = "Use buttons to discard a Secret Objective:";
        if ("redraw".equals(suffix)) {
            message += "\n> - A new secret will be automatically drawn for you";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    public static List<Button> getUnscoredSecretObjectiveDiscardButtons(Player player) {
        return getSODiscardButtonsWithSuffix(player, "");
    }

    public static List<Button> getSODiscardButtonsWithSuffix(Player player, String suffix) {
        Map<String, Integer> secretObjectives = player.getSecrets();
        List<Button> soButtons = new ArrayList<>();
        if (secretObjectives != null && !secretObjectives.isEmpty()) {
            for (Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Buttons.red("discardSecret_" + idValue + suffix, "(" + idValue + ") " + soName, CardEmojis.SecretObjective));
                }
            }
        }
        return soButtons;
    }

    public static void showUnscored(Game game, GenericInteractionCreateEvent event) {
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This command is disabled for fog mode");
            return;
        }
        List<String> defaultSecrets = Mapper.getDecks().get(game.getSoDeckID()).getNewShuffledDeck();
        List<String> currentSecrets = new ArrayList<>(defaultSecrets);
        for (Player player : game.getPlayers().values()) {
            if (player == null) {
                continue;
            }
            if (player.getSecretsScored() != null) {
                currentSecrets.removeAll(player.getSecretsScored().keySet());
            }
        }
        currentSecrets.removeAll(game.getSoToPoList());
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Unscored Action Phase Secrets: ").append("\n");
        int x = 1;
        for (String id : currentSecrets) {
            if (SecretObjectiveInfoService.getSecretObjectiveRepresentation(id).contains("Action Phase")) {
                sb.append(x).append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(id));
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Status Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (SecretObjectiveInfoService.getSecretObjectiveRepresentation(id).contains("Status Phase")) {
                appendSecretObjectiveRepresentation(game, sb, id, x);
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Agenda Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (SecretObjectiveInfoService.getSecretObjectiveRepresentation(id).contains("Agenda Phase")) {
                appendSecretObjectiveRepresentation(game, sb, id, x);
                x++;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    private static void appendSecretObjectiveRepresentation(Game game, StringBuilder sb, String id, int x) {
        if (ListPlayerInfoService.getObjectiveThreshold(id, game) > 0) {
            sb.append(x).append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(id));
            sb.append("> ");
            for (Player player : game.getRealPlayers()) {
                sb.append(player.getFactionEmoji()).append(": ").append(ListPlayerInfoService.getPlayerProgressOnObjective(id, game, player))
                    .append("/").append(ListPlayerInfoService.getObjectiveThreshold(id, game)).append(" ");
            }
            sb.append("\n");
        } else {
            sb.append(x).append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(id));
        }
    }
}
