package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.RefreshLeaderService;

@UtilityClass
class OvertimeAcd2ButtonHandler {

    @ButtonHandler("resolveOvertime")
    public static void resolveOvertime(Player player, Game game, ButtonInteractionEvent event) {
        if (player.getTg() < 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " needs at least 3 trade goods to resolve _Overtime_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        List<Button> buttons = getOvertimeButtons(game, player, 2);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has no exhausted components to ready with _Overtime_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        String spendMessage =
                player.toString() + " spent 3 trade goods " + player.gainTG(-3) + " to resolve _Overtime_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), spendMessage);
        sendOvertimeButtons(player, game, 2);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveOvertimeStep2_")
    public static void resolveOvertimeStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveOvertimeStep2_", "");
        String[] parts = payload.split("_", 3);
        if (parts.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remaining;
        try {
            remaining = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String readyItem = readyOvertimeComponent(player, game, parts[0], parts[2]);
        if (readyItem == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " could not ready that component with _Overtime_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " readied " + readyItem + " using _Overtime_.");
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            sendOvertimeButtons(player, game, remaining - 1);
        }
    }

    private static void sendOvertimeButtons(Player player, Game game, int remainingComponents) {
        List<Button> buttons = new ArrayList<>(getOvertimeButtons(game, player, remainingComponents));
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        String message = remainingComponents > 1
                ? player.getRepresentationUnfogged() + ", choose a component to ready with _Overtime_."
                : player.getRepresentationUnfogged() + ", you may ready 1 more component with _Overtime_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private static List<Button> getOvertimeButtons(Game game, Player player, int remainingComponents) {
        List<Button> buttons = new ArrayList<>();
        String prefix = "resolveOvertimeStep2_";

        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(
                    prefix + "planet_" + remainingComponents + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }

        for (String breakthrough : player.getBreakthroughIDs()) {
            if (player.isBreakthroughExhausted(breakthrough) && player.isBreakthroughUnlocked(breakthrough)) {
                BreakthroughModel breakthroughModel = Mapper.getBreakthrough(breakthrough);
                buttons.add(Buttons.blue(
                        prefix + "breakthrough_" + remainingComponents + "_" + breakthrough,
                        "Ready " + breakthroughModel.getName() + " Breakthrough",
                        player.getFactionEmoji()));
            }
        }

        for (Leader leader : player.getLeaders()) {
            if (leader.isExhausted()) {
                String leaderName =
                        leader.getLeaderModel().map(LeaderModel::getName).orElse(leader.getId());
                buttons.add(Buttons.gray(
                        prefix + "leader_" + remainingComponents + "_" + leader.getId(),
                        "Ready " + leaderName + (Constants.AGENT.equals(leader.getType()) ? " Agent" : " Leader"),
                        LeaderEmojis.getLeaderTypeEmoji(leader.getType())));
            }
        }

        for (String relic : player.getExhaustedRelics()) {
            RelicModel relicModel = Mapper.getRelic(relic);
            buttons.add(Buttons.red(
                    prefix + "relic_" + remainingComponents + "_" + relic, "Ready " + relicModel.getName() + " Relic"));
        }

        for (String tech : player.getExhaustedTechs()) {
            TechnologyModel techModel = Mapper.getTech(tech);
            buttons.add(Buttons.green(
                    prefix + "tech_" + remainingComponents + "_" + tech,
                    "Ready " + techModel.getName() + " Technology",
                    techModel.getCondensedReqsEmojis(true)));
        }

        for (String planet : player.getExhaustedPlanetsAbilities()) {
            PlanetModel planetModel = Mapper.getPlanet(planet);
            buttons.add(Buttons.blue(
                    prefix + "legendary_" + remainingComponents + "_" + planet,
                    "Ready " + planetModel.getName() + " Ability",
                    MiscEmojis.LegendaryPlanet));
        }

        return buttons;
    }

    private static String readyOvertimeComponent(Player player, Game game, String type, String componentId) {
        return switch (type) {
            case "planet" -> {
                if (!player.getExhaustedPlanets().remove(componentId)) {
                    yield null;
                }
                yield Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(componentId, game);
            }
            case "breakthrough" -> {
                if (!player.isBreakthroughExhausted(componentId) || !player.isBreakthroughUnlocked(componentId)) {
                    yield null;
                }
                player.setBreakthroughExhausted(componentId, false);
                yield player.getBreakthroughModel(componentId).getNameRepresentation();
            }
            case "leader" -> {
                Leader leader = player.getLeaderByID(componentId).orElse(null);
                if (leader == null || !leader.isExhausted()) {
                    yield null;
                }
                RefreshLeaderService.refreshLeader(player, leader, game);
                yield leader.getLeaderModel()
                        .map(LeaderModel::getNameRepresentation)
                        .orElse(componentId);
            }
            case "relic" -> {
                if (!player.getExhaustedRelics().contains(componentId)) {
                    yield null;
                }
                player.removeExhaustedRelic(componentId);
                RelicModel relicModel = Mapper.getRelic(componentId);
                yield relicModel == null ? componentId : relicModel.getNameRepresentation();
            }
            case "tech" -> {
                if (!player.isTechExhausted(componentId)) {
                    yield null;
                }
                player.refreshTech(componentId);
                TechnologyModel techModel = Mapper.getTech(componentId);
                yield techModel == null ? componentId : techModel.getNameRepresentation();
            }
            case "legendary" -> {
                if (!player.getExhaustedPlanetsAbilities().remove(componentId)) {
                    yield null;
                }
                PlanetModel planetModel = Mapper.getPlanet(componentId);
                yield planetModel == null ? componentId : planetModel.getLegendaryNameRepresentation();
            }
            default -> null;
        };
    }
}
