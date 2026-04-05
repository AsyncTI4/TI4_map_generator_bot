package ti4.buttons.handlers.milty;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.milty.MiltyDraftDisplayService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyService;
import ti4.service.regex.RegexService;

@UtilityClass
class MiltyDraftButtonHandlers {

    @ButtonHandler("showMiltyDraft")
    private void postDraftInfo(ButtonInteractionEvent event, Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        MiltyDraftDisplayService.repostDraftInformation(manager, game);
    }

    @ButtonHandler("milty_")
    private void doMiltyDraftPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.doMiltyPick(event, game, buttonID, player);
    }

    @ButtonHandler("restartMiltyQueue")
    private void restartMiltyQueue(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue(player.getUserID() + "queuedMiltyPick", "");
        MiltyDraftManager manager = game.getMiltyDraftManager();
        List<Button> buttons = manager.getQueueButtons(player, game);
        String msg = getMiltyQueueMessage(game, player);
        buttons.add(Buttons.gray("restartMiltyQueue", "Restart Queue"));
        if (buttons.size() == 1) {
            msg +=
                    "You can use this button to restart if some mistake was made. Otherwise one of these options should be selected for you when it is your turn to pick a strategy card.";
        } else {
            msg += "You can use these buttons to queue a pick.";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static String getMiltyQueueMessage(Game game, Player player) {
        String alreadyQueued = game.getStoredValue(player.getUserID() + "queuedMiltyPick");
        int numQueued = alreadyQueued.split("_").length;
        if (alreadyQueued.isEmpty()) {
            numQueued = 0;
        }
        StringBuilder msg =
                new StringBuilder(player.getRepresentationNoPing() + ", your queued picks are as follows:\n");
        if (numQueued > 0) {
            int count = 1;
            for (String num : alreadyQueued.split("_")) {
                if (num.isEmpty()) {
                    continue;
                }
                msg.append(count).append(". ");
                String pick = num.replace("fin", "_");
                String category = pick.substring(0, pick.indexOf('_'));
                String item = pick.substring(pick.indexOf('_') + 1);
                String name =
                        switch (category) {
                            case "slice" ->
                                MiltyDraftEmojis.getMiltyDraftEmoji(item).emojiString();
                            case "faction" ->
                                Mapper.getFaction(item).getFactionName() + " "
                                        + Mapper.getFaction(item).getFactionEmoji();
                            case "order" ->
                                MiltyDraftEmojis.getSpeakerPickEmoji(Integer.parseInt(item))
                                        .emojiString();
                            default -> "Unknown";
                        };
                if (item.startsWith("keleres"))
                    name = "The Council Keleres" + " " + Mapper.getFaction(item).getFactionEmoji();
                msg.append(name);
                msg.append('\n');
            }
        } else {
            msg.append("You currently have no queued picks.");
        }
        return msg.toString();
    }

    @ButtonHandler("queueMilyPick_")
    private void queueMiltyDraftPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        ButtonHelper.deleteMessage(event);
        if (manager.getCurrentDraftPlayer(game) == null
                && manager.getCurrentDraftPlayer(game).equals(player)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You are up to draft and you should just do that instead of queueing.");
            return;
        }
        String remainingID = buttonID.replace("queueMilyPick_", "").replace("_", "fin");
        game.setStoredValue(
                player.getUserID() + "queuedMiltyPick",
                game.getStoredValue(player.getUserID() + "queuedMiltyPick") + "_" + remainingID);
        List<Button> buttons = manager.getQueueButtons(player, game);
        String msg = getMiltyQueueMessage(game, player);
        buttons.add(Buttons.gray("restartMiltyQueue", "Restart Queue"));
        if (buttons.size() == 1) {
            msg +=
                    "You can use this button to restart if some mistake was made. Otherwise one of these options should be selected for you when it is your turn to pick a strategy card.";
        } else {
            msg +=
                    "You can use these buttons to queue another option in case all the ones you currently have queued are taken.";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("miltyFactionInfo_")
    private void sendAvailableFactionInfo(Game game, Player player, String buttonID) {
        if (player == null) return;
        String whichOnes = buttonID.replace("miltyFactionInfo_", "");
        List<FactionModel> displayFactions = new ArrayList<>();
        switch (whichOnes) {
            case "all" -> displayFactions.addAll(game.getMiltyDraftManager().allFactions());
            case "picked" -> displayFactions.addAll(game.getMiltyDraftManager().pickedFactions());
            case "remaining" ->
                displayFactions.addAll(game.getMiltyDraftManager().remainingFactions());
        }

        boolean first = true;
        List<MessageEmbed> embeds =
                displayFactions.stream().map(FactionModel::fancyEmbed).toList();
        for (MessageEmbed e : embeds) {
            String message = "";
            if (first) message = player.getRepresentationUnfogged() + " Here's an overview of the factions:";
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
            first = false;
        }
        if (!game.isTwilightsFallMode() && game.isThundersEdge()) {
            List<MessageEmbed> teEmbeds = new ArrayList<>();
            for (FactionModel faction : displayFactions) {
                String btId = faction.getID() + "bt";
                if (btId.contains("keleres")) {
                    btId = "keleresbt";
                }
                if (Mapper.getBreakthrough(btId) != null) {
                    teEmbeds.add(Mapper.getBreakthrough(btId).getRepresentationEmbed());
                }
            }
            first = true;
            for (MessageEmbed e : teEmbeds) {
                String message = "";
                if (first) {
                    message =
                            player.getRepresentationUnfogged() + ", here is an overview of the faction breakthroughs.";
                }
                MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
                first = false;
            }
        }
    }

    @ButtonHandler("draftPresetKeleres_")
    private void presetKeleresFlavor(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RegexService.runMatcher("draftPresetKeleres_(?<flavor>(mentak|xxcha|argent))", buttonID, matcher -> {
            String flavor = matcher.group("flavor");
            game.setStoredValue("keleresFlavorPreset", flavor);
            String preset = game.getStoredValue("keleresFlavorPreset");
            if (preset != null) {
                String keleresName =
                        Mapper.getFaction("keleres" + preset.charAt(0)).getFactionTitle();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully preset " + keleresName);
                MiltyService.offerKeleresSetupButtons(game.getMiltyDraftManager(), player);
                ButtonHelper.deleteMessage(event);
            }
        });
    }
}
