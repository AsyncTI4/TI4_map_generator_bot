package ti4.service.milty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.MapTemplateHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.map.AddTileListService;
import ti4.service.milty.MiltyDraftManager.PlayerDraft;

@UtilityClass
public class FinishDraftService {

    public FactionModel determineKeleresFlavor(MiltyDraftManager manager, Game game) {
        List<String> flavors = List.of("mentak", "xxcha", "argent");
        List<String> valid = flavors.stream().filter(Predicate.not(manager::isFactionTaken)).toList();
        String preset = game.getStoredValue("keleresFlavorPreset");
        if (valid.contains(preset)) return Mapper.getFaction("keleres" + preset.charAt(0));
        if (valid.size() == 1) return Mapper.getFaction(valid.getFirst());
        return null;
    }

    public void finishDraft(GenericInteractionCreateEvent event, MiltyDraftManager manager, Game game) {
        MessageChannel mainGameChannel = game.getMainGameChannel();
        try {
            MiltyDraftHelper.buildPartialMap(game, event);
            boolean keleresExists = false;
            for (String playerId : manager.getPlayers()) {
                Player player = game.getPlayer(playerId);
                PlayerDraft picks = manager.getPlayerDraft(playerId);
                String color = player.getNextAvailableColour();
                if (playerId.equals(Constants.chassitId) && game.getUnusedColorsPreferringBase().contains(Mapper.getColor("lightgray"))) {
                    color = "lightgray";
                }
                String faction = picks.getFaction();
                String pos = MapTemplateHelper.getPlayerHomeSystemLocation(picks, manager.getMapTemplate());
                boolean speaker = picks.getPosition() == 1;

                if (faction.startsWith("keleres")) {
                    FactionModel preset = determineKeleresFlavor(manager, game);
                    if (preset != null) {
                        faction = preset.getAlias();
                    } else {
                        faction = null;
                        keleresExists = true;
                        Set<String> allowed = new HashSet<>(Set.of("mentak", "xxcha", "argent"));
                        for (PlayerDraft pd : manager.getDraft().values()) {
                            allowed.remove(pd.getFaction());
                        }
                        List<Button> buttons = new ArrayList<>();
                        String message = player.getPing() + " choose a flavor of keleres:";
                        for (String flavor : allowed) {
                            String emoji = Mapper.getFaction(flavor).getFactionEmoji();
                            String keleres = "keleres" + flavor.charAt(0);
                            String id = String.format("setupStep5_%s_%s_%s_%s_%s", player.getUserID(), keleres, color, pos, speaker ? "yes" : "no");
                            String msg = "Keleres (" + flavor + ")";
                            Button butt = Buttons.green(id, msg).withEmoji(Emoji.fromFormatted(emoji));
                            buttons.add(butt);
                        }
                        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, buttons);
                    }
                }

                if (faction != null) {
                    MiltyService.secondHalfOfPlayerSetup(player, game, color, faction, pos, event, speaker);
                }
            }
            game.setPhaseOfGame("playerSetup");
            AddTileListService.finishSetup(game, event);
            if (keleresExists) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getPing()
                    + " be sure to wait for Keleres to choose their flavour and starting tech before dealing out secret objectives.");
            }
            game.getMiltyDraftManager().setFinished(true);
        } catch (Exception e) {
            StringBuilder error = new StringBuilder("Something went wrong and the map could not be built automatically. Here are the slice strings if you wish to try doing it manually: ");
            List<PlayerDraft> speakerOrdered = manager.getDraft().values().stream()
                .sorted(Comparator.comparing(PlayerDraft::getPosition))
                .toList();
            int index = 1;
            for (PlayerDraft d : speakerOrdered) {
                error.append("\n").append(index).append(". ").append(d.getSlice().ttsString());
            }
            MessageHelper.sendMessageToChannel(mainGameChannel, error.toString());
            BotLogger.error(new BotLogger.LogMessageOrigin(event, game), e.getMessage(), e);
        }
    }
}