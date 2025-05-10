package ti4.buttons.handlers.milty;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.milty.DraftDisplayService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyService;
import ti4.service.regex.RegexService;

@UtilityClass
public class MiltyDraftButtonHandlers {

    @ButtonHandler("showMiltyDraft")
    private void postDraftInfo(ButtonInteractionEvent event, Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        DraftDisplayService.repostDraftInformation(event, manager, game);
    }

    @ButtonHandler("milty_")
    private void doMiltyDraftPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.doMiltyPick(event, game, buttonID, player);
    }

    @ButtonHandler("miltyFactionInfo_")
    private void sendAvailableFactionInfo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null) return;
        String whichOnes = buttonID.replace("miltyFactionInfo_", "");
        List<FactionModel> displayFactions = new ArrayList<>();
        switch (whichOnes) {
            case "all" -> displayFactions.addAll(game.getMiltyDraftManager().allFactions());
            case "picked" -> displayFactions.addAll(game.getMiltyDraftManager().pickedFactions());
            case "remaining" -> displayFactions.addAll(game.getMiltyDraftManager().remainingFactions());
        }

        boolean first = true;
        List<MessageEmbed> embeds = displayFactions.stream().map(FactionModel::fancyEmbed).toList();
        for (MessageEmbed e : embeds) {
            String message = "";
            if (first)
                message = player.getRepresentationUnfogged() + " Here's an overview of the factions:";
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
            first = false;
        }
    }

    @ButtonHandler("draftPresetKeleres_")
    private void presetKeleresFlavor(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RegexService.runMatcher("draftPresetKeleres_(?<flavor>(mentak|xxcha|argent))", buttonID, matcher -> {
            String flavor = matcher.group("flavor");
            game.setStoredValue("keleresFlavorPreset", flavor);
            String preset = game.getStoredValue("keleresFlavorPreset");
            if (preset != null) {
                String keleresName = Mapper.getFaction("keleres" + preset.charAt(0)).getFactionTitle();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully preset " + keleresName);
                MiltyService.offerKeleresSetupButtons(game.getMiltyDraftManager(), player);
                ButtonHelper.deleteMessage(event);
            }
        });
    }
}