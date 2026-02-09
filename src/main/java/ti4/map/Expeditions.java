package ti4.map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Data;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@Data
public class Expeditions {

    @JsonIgnore
    private Game game;

    private Map<String, String> expeditionFactions = new LinkedHashMap<>();

    public String getTechSkip() {
        return expeditionFactions.get("techSkip");
    }

    public String getTradeGoods() {
        return expeditionFactions.get("tradeGoods");
    }

    public String getFiveRes() {
        return expeditionFactions.get("fiveRes");
    }

    public String getFiveInf() {
        return expeditionFactions.get("fiveInf");
    }

    public String getSecret() {
        return expeditionFactions.get("secret");
    }

    public String getActionCards() {
        return expeditionFactions.get("actionCards");
    }

    public String setTechSkip(String value) {
        return expeditionFactions.put("techSkip", value);
    }

    public String setTradeGoods(String value) {
        return expeditionFactions.put("tradeGoods", value);
    }

    public String setFiveRes(String value) {
        return expeditionFactions.put("fiveRes", value);
    }

    public String setFiveInf(String value) {
        return expeditionFactions.put("fiveInf", value);
    }

    public String setSecret(String value) {
        return expeditionFactions.put("secret", value);
    }

    public String setActionCards(String value) {
        return expeditionFactions.put("actionCards", value);
    }

    public Expeditions(Game game) {
        this.game = game;
        expeditionFactions.put("techSkip", null);
        expeditionFactions.put("tradeGoods", null);
        expeditionFactions.put("fiveRes", null);
        expeditionFactions.put("fiveInf", null);
        expeditionFactions.put("secret", null);
        expeditionFactions.put("actionCards", null);
    }

    @JsonIgnore
    public int getMostCompleteByAny() {
        Map<String, Long> factionCounts =
                expeditionFactions.values().stream().collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        long most = factionCounts.values().stream()
                .max(Comparator.comparingLong(x -> x))
                .orElse(0L);
        return (int) most;
    }

    @JsonIgnore
    public List<String> getFactionsWithMostComplete() {
        Map<String, Long> factionCounts =
                expeditionFactions.values().stream().collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        long most = factionCounts.values().stream()
                .max(Comparator.comparingLong(x -> x))
                .orElse(0L);
        return new ArrayList<>(factionCounts.entrySet().stream()
                .filter(e -> e.getValue() == most)
                .map(Entry::getKey)
                .toList());
    }

    @JsonIgnore
    public int getRemainingExpeditionCount() {
        return (int) expeditionFactions.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .count();
    }

    @JsonIgnore
    public String getTopLevelExpeditionButtonText() {
        int count = getRemainingExpeditionCount();
        if (count > 0) {
            return "Do an Expedition (" + count + " Remaining)";
        } else {
            return null;
        }
    }

    @JsonIgnore
    private String playerInfo(Game game, Player viewingPlayer, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player != null
                ? (game.isFowMode() && !FoWHelper.canSeeStatsOfPlayer(game, player, viewingPlayer)
                        ? "Someone"
                        : player.getRepresentation(false, false))
                : "-";
    }

    @JsonIgnore
    private TI4Emoji getExpeditionEmoji(String expeditionID, Game game) {
        return switch (expeditionID) {
            case "techSkip" -> TechEmojis.PropulsionTech;
            case "tradeGoods" -> MiscEmojis.tg;
            case "fiveRes" -> MiscEmojis.Resources_5;
            case "fiveInf" -> MiscEmojis.Influence_5;
            case "secret" -> CardEmojis.SecretObjective;
            case "actionCards" -> CardEmojis.getACEmoji(game);
            default -> null;
        };
    }

    @JsonIgnore
    private String getExpeditionMessage(String expeditionID) {
        return switch (expeditionID) {
            case "techSkip" -> "Exhaust 1 technology specialty planet";
            case "tradeGoods" -> "Spend 3 trade goods";
            case "fiveRes" -> "Spend 5 resources";
            case "fiveInf" -> "Spend 5 influence";
            case "secret" -> "Discard 1 secret objective";
            case "actionCards" -> "Discard 2 action cards";
            default -> null;
        };
    }

    @JsonIgnore
    public String printExpeditionInfo(Game game, Player player) {
        StringBuilder sb = new StringBuilder("Thunder's Edge Expedition Status:");
        for (Entry<String, String> exp : expeditionFactions.entrySet()) {
            sb.append("\n> ").append(getExpeditionEmoji(exp.getKey(), game));
            sb.append(" ").append(playerInfo(game, player, exp.getValue()));
        }
        return sb.toString();
    }

    @JsonIgnore
    public List<Button> getRemainingExpeditionButtons(Player player) {
        String prefix = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();
        for (Entry<String, String> exp : expeditionFactions.entrySet()) {
            if (exp.getValue() != null) continue;
            String id = prefix + "TEexpedition_" + exp.getKey();
            String msg = getExpeditionMessage(exp.getKey());
            buttons.add(Buttons.green(id, msg, getExpeditionEmoji(exp.getKey(), player.getGame())));
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static void setExpedition(Game game, String expedition, String faction) {
        Expeditions exp = game.getExpeditions();
        exp.expeditionFactions.put(expedition, faction);
    }

    @ButtonHandler("TEexpedition_")
    private static void handleExpeditionButtonPress(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String expeditionType = buttonID.replace("TEexpedition_", "");
        Expeditions exp = game.getExpeditions();
        MessageChannel channel = player.getCorrectChannel();
        String output;
        boolean success = false;
        String whichExp = "### " + player.getRepresentation() + " completed the **%s** expedition!";
        switch (expeditionType) {
            case "techSkip" -> {
                success = true;
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "skips");
                buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), String.format(whichExp, "technology specialty"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "Please choose the planet with a technology specialty that you wish to exhaust.",
                        buttons);
            }
            case "tradeGoods" -> {
                success = true;
                int oldTg = player.getTg();
                MessageHelper.sendMessageToChannel(channel, String.format(whichExp, "3 trade goods"));
                List<Button> buttons = null;
                if (oldTg < 3) {
                    output =
                            "You do not have enough trade goods to do this automatically. Use these buttons to spend trade goods.";
                    buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "tgsonly");
                    buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Spending Trade Goods"));
                } else {
                    player.setTg(player.getTg() - 3);
                    output = player.getRepresentation() + " Automatically deducted 3 trade goods (" + oldTg + "->"
                            + player.getTg() + ")";
                }
                MessageHelper.sendMessageToChannelWithButtons(channel, output, buttons);
            }
            case "fiveRes" -> {
                success = true;
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
                MessageHelper.sendMessageToChannel(channel, String.format(whichExp, "5 resources"));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, "Use these buttons to spend 5 resources.", buttons);
            }
            case "fiveInf" -> {
                success = true;
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
                MessageHelper.sendMessageToChannel(channel, String.format(whichExp, "5 influence"));
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, "Use these buttons to spend 5 influence.", buttons);
            }
            case "secret" -> {
                success = true;
                output = String.format(whichExp, "secret objective");
                List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player);
                if (!soButtons.isEmpty()) {
                    output += "\n-# Use the buttons below to discard an unscored secret objective.";
                    MessageHelper.sendMessageToChannel(channel, output);
                    MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                            event, "Use these buttons to discard a secret objective:", soButtons);
                } else {
                    output +=
                            "\n-# you may not have an unscored secret to discard... use `/game undo` if this was a mistake";
                    MessageHelper.sendMessageToChannel(channel, output);
                }
            }
            case "actionCards" -> {
                success = true;
                output = String.format(whichExp, "2 action cards");
                List<Button> acButtons = ActionCardHelper.getDiscardActionCardButtons(player, false);
                if (acButtons.size() >= 2) {
                    output += "\n-# Use the buttons in your private channel to discard 2 action cards.";
                    MessageHelper.sendMessageToChannel(channel, output);
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(),
                            player.getRepresentation() + "Use these buttons to discard action cards.",
                            acButtons);
                } else {
                    output += "\n-# you may not have enough action cards... use `/game undo` if this was a mistake";
                    MessageHelper.sendMessageToChannel(channel, output);
                }
            }
        }

        // The player clicked the button and succeeded
        if (success) {
            exp.expeditionFactions.put(expeditionType, player.getFaction());
            if (exp.getRemainingExpeditionCount() == 0) {
                String message = !game.isFowMode() ? "# ATTENTION " + game.getPing() + "\n" : "";
                message += player.getRepresentation()
                        + " has completed the last expedition! They can now place the Thunder's Edge planet on the board:";
                message +=
                        "\n-# Thunder's Edge must be placed on a tile that does not have any planets or printed wormholes, and cannot be placed in a supernova or The Fracture.";
                Button button = Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "placeThundersEdge", "Place Thunder's Edge");
                MessageHelper.sendMessageToChannel(channel, message);
                MessageHelper.sendMessageToChannelWithButton(
                        channel, "Use the button to begin placing Thunder's Edge:", button);
            }
            BreakthroughCommandHelper.unlockAllBreakthroughs(game, player);
        }
        event.getHook()
                .editOriginal(exp.printExpeditionInfo(game, player))
                .setComponents()
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
