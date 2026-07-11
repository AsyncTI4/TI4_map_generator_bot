package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.planet.PlanetService;

@UtilityClass
class ReconstructionAcd2ButtonHandler {

    @ButtonHandler("resolveReconstruction")
    public static void resolveReconstruction(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (activeSystem == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " could not resolve _Reconstruction_ because there is no active system.");
            return;
        }

        List<Button> buttons = getReconstructionPlanetButtons(game, player, activeSystem);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has no planets they control in the active system for _Reconstruction_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", choose a planet you control in "
                        + activeSystem.getRepresentationForButtons(game, player) + " for _Reconstruction_.",
                buttons);
    }

    @ButtonHandler("reconstructionStep2_")
    public static void resolveReconstructionStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("reconstructionStep2_", "");
        Planet planetHolder = game.getPlanet(planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (planetHolder == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " could not find that planet for _Reconstruction_.");
            return;
        }

        PlanetService.refreshPlanet(player, planet);
        List<Button> buttons = getReconstructionTraitButtons(game, planetHolder);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " readied "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                            + " with _Reconstruction_. It has no planet trait, so the exploration deck portion cannot occur.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + " readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                        + " with _Reconstruction_. Choose the matching exploration deck to reveal from.",
                buttons);
    }

    @ButtonHandler("reconstructionStep3_")
    public static void resolveReconstructionStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String info = buttonID.replace("reconstructionStep3_", "");
        int splitIndex = info.lastIndexOf('_');
        if (splitIndex < 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " could not resolve _Reconstruction_.");
            return;
        }
        String planet = info.substring(0, splitIndex);
        String trait = info.substring(splitIndex + 1);
        Tile tile = game.getTileContainingPlanet(planet);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " could not find that planet's system for _Reconstruction_.");
            return;
        }

        StringBuilder sb = new StringBuilder(player.toString())
                .append(" revealed the top 3 cards of the ")
                .append(StringUtils.capitalize(trait))
                .append(" exploration deck for _Reconstruction_ on ")
                .append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game))
                .append(':');

        for (int x = 0; x < 3; x++) {
            String cardId = game.drawExplore(trait);
            if (cardId == null) {
                sb.append("\n> No more cards were available in that deck.");
                break;
            }

            ExploreModel explore = Mapper.getExplore(cardId);
            if (explore == null) {
                continue;
            }

            sb.append("\n> Revealed ").append(explore.getNameRepresentation());
            if (Constants.ATTACH.equalsIgnoreCase(explore.getResolution())) {
                sb.append(" and resolved the attachment.");
                String messageText = player.toString() + " resolved an attachment with _Reconstruction_ on "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game) + ":";
                ExploreService.resolveExplore(event, cardId, tile, planet, messageText, player, game);
            } else {
                sb.append(" and discarded it.");
            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    private static List<Button> getReconstructionPlanetButtons(Game game, Player player, Tile activeSystem) {
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : activeSystem.getPlanetUnitHolders()) {
            if (player.containsPlanet(planet.getName())) {
                buttons.add(Buttons.gray(
                        "reconstructionStep2_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    private static List<Button> getReconstructionTraitButtons(Game game, Planet planet) {
        String planetId = planet.getName();
        List<Button> buttons = new ArrayList<>();
        Set<String> explorationTraits = new HashSet<>(planet.getPlanetTypes());
        for (String trait : explorationTraits) {
            if ("cultural".equals(trait)) {
                buttons.add(Buttons.blue(
                        "reconstructionStep3_" + planetId + "_" + trait, "Cultural", ExploreEmojis.Cultural));
            }
            if ("industrial".equals(trait)) {
                buttons.add(Buttons.green(
                        "reconstructionStep3_" + planetId + "_" + trait, "Industrial", ExploreEmojis.Industrial));
            }
            if ("hazardous".equals(trait)) {
                buttons.add(Buttons.red(
                        "reconstructionStep3_" + planetId + "_" + trait, "Hazardous", ExploreEmojis.Hazardous));
            }
        }
        return buttons;
    }
}
