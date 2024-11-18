package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.ListPlayerInfoService;

public class SecretObjectiveHelper {

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
            if (ListPlayerInfoService.getObjectiveThreshold(entry.getKey(), game) > 0) {
                message.append(getSecretObjectiveRepresentationNoNewLine(entry.getKey()));
                message.append(" (").append(ListPlayerInfoService.getPlayerProgressOnObjective(entry.getKey(), game, player)).append("/").append(ListPlayerInfoService.getObjectiveThreshold(entry.getKey(), game)).append(")\n");
            } else {
                message.append(getSecretObjectiveRepresentation(entry.getKey()));
            }
            //message.append(getSecretObjectiveRepresentation(entry.getKey())).append("\n");
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
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), msg, buttons);
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

                    CommanderUnlockCheck.checkAllPlayersInGame(game, "lanefir");

                    String message2 = player.getRepresentation() + " purged fragments: " + fragmentsToPurge;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
                } else {
                    Player p1 = player;
                    String finChecker = p1.getFinsFactionCheckerPrefix();
                    List<Button> purgeFragButtons = new ArrayList<>();
                    if (p1.getCrf() > 0) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_1", "Purge 1 Cultural Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getIrf() > 0) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_1",
                            "Purge 1 Industrial Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getHrf() > 0) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_1", "Purge 1 Hazardous Fragment");
                        purgeFragButtons.add(transact);
                    }
                    if (p1.getUrf() > 0) {
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
        sendSecretObjectiveInfo(game, player);
        Helper.checkIfHeroUnlocked(game, player);
        CommanderUnlockCheck.checkPlayer(player, "nomad");
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
            sb.append(getSecretObjectiveRepresentation(id)).append("\n");
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player_, game, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "All SOs shown to player");
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player) {
        //SO INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, getSecretObjectiveCardInfo(game, player));

        if (player.getSecretsUnscored().isEmpty()) return;

        // SCORE/DISCARD BUTTONS
        String secretMsg = "_ _\nClick a button to either score or discard a secret objective";
        List<Button> buttons = new ArrayList<>();
        Button scoreB = Buttons.blue("get_so_score_buttons", "Score an SO");
        Button discardB = Buttons.red("get_so_discard_buttons", "Discard an SO");
        ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread();
        buttons.add(scoreB);
        buttons.add(discardB);
        MessageHelper.sendMessageToChannelWithButtons(cardsInfoThreadChannel, secretMsg, buttons);
    }

    public static String getSecretObjectiveRepresentationShort(String soID) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        sb.append(Emojis.SecretObjective).append("__").append(soName).append("__").append("\n");
        return sb.toString();
    }

    public static String getSecretObjectiveRepresentation(String soID) {
        return getSecretObjectiveRepresentation(soID, true);
    }

    public static String getSecretObjectiveRepresentationNoNewLine(String soID) {
        return getSecretObjectiveRepresentation(soID, false);
    }

    private static String getSecretObjectiveRepresentation(String soID, boolean newLine) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        String soPhase = so.getPhase();
        String soDescription = so.getText();
        if (newLine) {
            sb.append(Emojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        } else {
            sb.append(Emojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription);
        }
        return sb.toString();
    }

    private static String getSecretObjectiveCardInfo(Game game, Player player) {
        Map<String, Integer> secretObjective = player.getSecrets();
        Map<String, Integer> scoredSecretObjective = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;

        //SCORED SECRET OBJECTIVES
        sb.append("**Scored Secret Objectives (").append(player.getSoScored()).append("/").append(player.getMaxSOCount()).append("):**").append("\n");
        if (scoredSecretObjective.isEmpty()) {
            sb.append("> None");
        } else {
            for (Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                sb.append("`").append(index).append(".").append(Helper.leftpad("(" + so.getValue(), 4)).append(")`");
                sb.append(getSecretObjectiveRepresentationShort(so.getKey()));
                index++;
            }
        }
        sb.append("\n");

        //UNSCORED SECRET OBJECTIVES
        sb.append("**Unscored Secret Objectives:**").append("\n");
        if (secretObjective != null) {
            if (secretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                    Integer idValue = so.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + idValue, 4)).append(")`");

                    if (ListPlayerInfoService.getObjectiveThreshold(so.getKey(), game) > 0) {
                        sb.append(getSecretObjectiveRepresentationNoNewLine(so.getKey()));
                        sb.append(" (").append(ListPlayerInfoService.getPlayerProgressOnObjective(so.getKey(), game, player)).append("/").append(ListPlayerInfoService.getObjectiveThreshold(so.getKey(), game)).append(")\n");
                    } else {
                        sb.append(getSecretObjectiveRepresentation(so.getKey()));
                    }
                    index++;
                }
            }
        }
        return sb.toString();
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
                    soButtons.add(Buttons.blue(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName, Emojis.SecretObjective));
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
                    soButtons.add(Buttons.red("discardSecret_" + idValue + suffix, "(" + idValue + ") " + soName, Emojis.SecretObjective));
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
            if (getSecretObjectiveRepresentation(id).contains("Action Phase")) {
                sb.append(x).append(getSecretObjectiveRepresentation(id));
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Status Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (getSecretObjectiveRepresentation(id).contains("Status Phase")) {
                appendSecretObjectiveRepresentation(game, sb, id, x);
                x++;
            }
        }
        x = 1;
        sb.append("\n").append("Unscored Agenda Phase Secrets: ").append("\n");
        for (String id : currentSecrets) {
            if (getSecretObjectiveRepresentation(id).contains("Agenda Phase")) {
                appendSecretObjectiveRepresentation(game, sb, id, x);
                x++;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    private static void appendSecretObjectiveRepresentation(Game game, StringBuilder sb, String id, int x) {
        if (ListPlayerInfoService.getObjectiveThreshold(id, game) > 0) {
            sb.append(x).append(getSecretObjectiveRepresentation(id));
            sb.append("> ");
            for (Player player : game.getRealPlayers()) {
                sb.append(player.getFactionEmoji()).append(": ").append(ListPlayerInfoService.getPlayerProgressOnObjective(id, game, player))
                    .append("/").append(ListPlayerInfoService.getObjectiveThreshold(id, game)).append(" ");
            }
            sb.append("\n");
        } else {
            sb.append(x).append(getSecretObjectiveRepresentation(id));
        }
    }
}
