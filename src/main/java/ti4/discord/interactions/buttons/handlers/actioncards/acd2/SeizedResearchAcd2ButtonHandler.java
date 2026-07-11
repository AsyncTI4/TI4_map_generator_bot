package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
class SeizedResearchAcd2ButtonHandler {

    @ButtonHandler("resolveSeizedResearch")
    public static void resolveSeizedResearch(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getSeizedResearchPlanetButtons(player, game);
        ButtonHelper.deleteMessage(event);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you do not control a planet with a technology specialty for _Seized Research_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the planet you gained control of for _Seized Research_.",
                buttons);
    }

    @ButtonHandler("seizedResearchPlanet_")
    public static void resolveSeizedResearchPlanet(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.replace("seizedResearchPlanet_", "");
        Planet planet = game.getPlanet(planetName);
        if (planet == null
                || !player.hasPlanet(planetName)
                || planet.getTechSpecialities().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Seized Research_ for that planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String specialty : planet.getTechSpecialities()) {
            TechSpecialty techSpecialty = parseTechSpecialty(specialty);
            if (techSpecialty == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "seizedResearchSpecialty_" + planetName + "_" + techSpecialty,
                    getResearchLabel(techSpecialty),
                    techSpecialty.getEmoji()));
        }
        if (hasLockedBreakthrough(player)) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "seizedResearchBreakthrough",
                    getBreakthroughLabel(player),
                    "Breakthrough"));
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no eligible _Seized Research_ options remaining for "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + ".");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Seized Research_ for "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + ".",
                buttons);
    }

    @ButtonHandler("seizedResearchSpecialty_")
    public static void resolveSeizedResearchSpecialty(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("seizedResearchSpecialty_", "");
        int separator = payload.lastIndexOf('_');
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Seized Research_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = payload.substring(0, separator);
        TechSpecialty specialty = parseTechSpecialty(payload.substring(separator + 1));
        Planet planet = game.getPlanet(planetName);
        if (specialty == null
                || planet == null
                || !player.hasPlanet(planetName)
                || !planet.getTechSpecialities().contains(specialty.toString())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Seized Research_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<TechnologyModel> techs = getEligibleTechsForSpecialty(game, player, specialty);
        ButtonHelper.deleteMessage(event);
        if (techs.isEmpty()) {
            String breakthroughMessage =
                    hasLockedBreakthrough(player) ? " You may still use the breakthrough option instead." : "";
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has no eligible "
                            + specialty.toString().replace("skip", " skip")
                            + " technologies to gain with _Seized Research_." + breakthroughMessage);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which technology to research with _Seized Research_"
                        + " from " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game)
                        + ".",
                ListTechService.getTechButtons(techs, player, "free"));
    }

    @ButtonHandler("seizedResearchBreakthrough")
    public static void resolveSeizedResearchBreakthrough(Player player, Game game, ButtonInteractionEvent event) {
        List<String> lockedBreakthroughs = player.getBreakthroughIDs().stream()
                .filter(bt -> !player.isBreakthroughUnlocked(bt))
                .toList();
        ButtonHelper.deleteMessage(event);
        if (lockedBreakthroughs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " already has their breakthrough unlocked.");
            return;
        }

        BreakthroughCommandHelper.unlockBreakthroughs(game, player, lockedBreakthroughs);
    }

    private static List<Button> getSeizedResearchPlanetButtons(Player player, Game game) {
        return player.getPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(planet ->
                        planet != null && !planet.getTechSpecialities().isEmpty())
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "seizedResearchPlanet_" + planet.getName(),
                        Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet.getName(), game)))
                .toList();
    }

    private static List<TechnologyModel> getEligibleTechsForSpecialty(
            Game game, Player player, TechSpecialty specialty) {
        return switch (specialty) {
            case BIOTIC ->
                ListTechService.getAllTechOfAType(game, TechnologyType.BIOTIC.toString(), player, false, true);
            case CYBERNETIC ->
                ListTechService.getAllTechOfAType(game, TechnologyType.CYBERNETIC.toString(), player, false, true);
            case PROPULSION ->
                ListTechService.getAllTechOfAType(game, TechnologyType.PROPULSION.toString(), player, false, true);
            case WARFARE ->
                ListTechService.getAllTechOfAType(game, TechnologyType.WARFARE.toString(), player, false, true);
            case UNITSKIP ->
                ListTechService.getAllTechOfAType(game, TechnologyType.UNITUPGRADE.toString(), player, false, true);
            case NONUNITSKIP -> getEligibleNonUnitTechs(game, player);
        };
    }

    private static List<TechnologyModel> getEligibleNonUnitTechs(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyType type : TechnologyType.mainFour) {
            techs.addAll(ListTechService.getAllTechOfAType(game, type.toString(), player, false, true));
        }
        return techs;
    }

    private static TechSpecialty parseTechSpecialty(String specialty) {
        try {
            return TechSpecialty.valueOf(specialty.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean hasLockedBreakthrough(Player player) {
        return player.getBreakthroughIDs().stream().anyMatch(bt -> !player.isBreakthroughUnlocked(bt));
    }

    private static String getBreakthroughLabel(Player player) {
        BreakthroughModel breakthrough = player.getBreakthroughIDs().stream()
                .map(player::getBreakthroughModel)
                .filter(model -> model != null)
                .findFirst()
                .orElse(null);
        if (breakthrough == null) {
            return "Gain Your Breakthrough";
        }
        return "Gain " + breakthrough.getName();
    }

    private static String getResearchLabel(TechSpecialty specialty) {
        return switch (specialty) {
            case BIOTIC -> "Research a Biotic Technology";
            case CYBERNETIC -> "Research a Cybernetic Technology";
            case PROPULSION -> "Research a Propulsion Technology";
            case WARFARE -> "Research a Warfare Technology";
            case UNITSKIP -> "Research a Unit Upgrade";
            case NONUNITSKIP -> "Research a Non-Unit Technology";
        };
    }
}
