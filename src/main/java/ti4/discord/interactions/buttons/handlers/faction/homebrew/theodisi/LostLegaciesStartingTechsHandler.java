package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class LostLegaciesStartingTechsHandler {
    private static final String GET_THURVIALI_STARTING_TECHS = "getThurvialiStartingTechs";
    private static final String SELECT_THURVIALI_STARTING_TECH_PLAYER = "selectThurvialiStartingTechPlayer_";
    private static final String THURVIALI_STARTING_TECH = "thurvialiStartingTech_";

    public static boolean offerStartingTechButtons(Game game, Player player, String startingTechFaction) {
        if (game == null || player == null) {
            return false;
        }

        String factionToCheck = startingTechFaction;
        if (factionToCheck == null || factionToCheck.isBlank()) {
            factionToCheck = player.getFaction();
        }
        if (factionToCheck == null || factionToCheck.isBlank()) {
            return false;
        }

        if (!isSupportedFaction(factionToCheck)) {
            return false;
        }

        if ("thurviali".equalsIgnoreCase(factionToCheck)) {
            offerThurvialiStartingTechs(game, player);
            return true;
        }

        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " press this button to get your starting technology.",
                Buttons.green(
                        player.factionButtonChecker() + "getLostLegaciesStartingTechOptions", "Get Starting Tech"));
        return true;
    }

    @ButtonHandler("getLostLegaciesStartingTechOptions")
    public static void handleStartingTechButton(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        ButtonHelper.deleteMessage(event);
        String factionToCheck = player.getFaction();
        if (factionToCheck == null || factionToCheck.isBlank()) {
            return;
        }

        switch (factionToCheck.toLowerCase()) {
            case "arcanum" -> offerArcanumStartingTechs(game, player);
            case "aeterna" -> offerAeternaStartingTechs(game, player);
            case "revenant" -> offerRevenantStartingTechs(game, player);
            case "scrapyard" -> gainRandomScrapyardStartTechs(game, player);
            case "thurviali" -> offerThurvialiStartingTechs(game, player);
            default -> {}
        }
    }

    public static boolean isSupportedFaction(String faction) {
        if (faction == null || faction.isBlank()) {
            return false;
        }
        return switch (faction.toLowerCase()) {
            case "arcanum", "aeterna", "revenant", "scrapyard", "thurviali" -> true;
            default -> false;
        };
    }

    public static void offerThurvialiStartingTechs(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        game.setStoredValue(THURVIALI_STARTING_TECH + player.getFaction(), "yes");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " gain the starting technologies of another player. **Wait until every other player has chosen their starting technologies before doing this.**",
                Buttons.green(
                        player.factionButtonChecker() + GET_THURVIALI_STARTING_TECHS, "Gain Starting Technologies"));
    }

    @ButtonHandler(GET_THURVIALI_STARTING_TECHS)
    public static void offerThurvialiStartingTechPlayerButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || (!"thurviali".equals(player.getFaction())
                        && game.getStoredValue(THURVIALI_STARTING_TECH + player.getFaction())
                                .isEmpty())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(other -> other != player)
                .filter(other -> !other.getTechs().isEmpty())
                .map(other -> Buttons.green(
                        player.factionButtonChecker() + SELECT_THURVIALI_STARTING_TECH_PLAYER + other.getFaction(),
                        "Gain " + other.getColor() + " Starting Technologies",
                        other.getFactionEmojiOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " choose the player whose starting technologies you will gain.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_THURVIALI_STARTING_TECH_PLAYER)
    public static void gainThurvialiStartingTechnologies(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || (!"thurviali".equals(player.getFaction())
                        && game.getStoredValue(THURVIALI_STARTING_TECH + player.getFaction())
                                .isEmpty())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player target =
                game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_THURVIALI_STARTING_TECH_PLAYER.length()));
        if (target == null || target == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<TechnologyModel> gainedTechs = target.getTechs().stream()
                .filter(tech -> !player.hasTech(tech))
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .toList();
        gainedTechs.forEach(tech -> player.addTech(tech.getAlias()));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " gained "
                        + (gainedTechs.isEmpty()
                                ? "no new technologies"
                                : gainedTechs.stream()
                                        .map(tech -> tech.getRepresentation(false))
                                        .collect(java.util.stream.Collectors.joining(", ")))
                        + " from " + target.getRepresentationNoPing() + "'s starting technologies.");
        game.removeStoredValue(THURVIALI_STARTING_TECH + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static void clearThurvialiStartingTech(Game game, Player player) {
        if (game != null && player != null) {
            game.removeStoredValue(THURVIALI_STARTING_TECH + player.getFaction());
        }
    }

    public static void offerArcanumStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 0);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your first non-faction starting technology. You must choose **2 technologies in the same color with no prerequisites**.",
                false);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your second non-faction starting technology. It must have the **same color** as your first choice and have no prerequisites.",
                false);
    }

    public static void offerAeternaStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 1);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your first non-faction starting technology. You must choose **2 technologies in different colors with 1 total prerequisite**. Choose one zero-prerequisite technology and one one-prerequisite technology.",
                false);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your second non-faction starting technology. It must have a **different color** from your first choice, and the two choices must have **1 total prerequisite**.",
                false);
    }

    public static void offerRevenantStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 0);
        String rule =
                "You may choose up to **2 non-faction technologies with no prerequisites owned by no other player**. "
                        + "All zero-prerequisite technologies are listed, so verify that no other player owns your choice.";
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged() + " choose your first starting technology, or press **Done**. "
                        + rule,
                true);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged() + " choose your second starting technology, or press **Done**. "
                        + rule,
                true);
    }

    public static void gainRandomScrapyardStartTechs(Game game, Player player) {
        List<TechnologyModel> randomTechs = new ArrayList<>(eligibleTechnologies(game, player, 0));
        Collections.shuffle(randomTechs);
        randomTechs = randomTechs.stream().limit(3).toList();

        for (TechnologyModel tech : randomTechs) {
            player.addTech(tech.getAlias());
        }
        if (!randomTechs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " gained starting technologies: "
                            + randomTechs.stream()
                                    .map(tech -> tech.getRepresentation(false))
                                    .collect(Collectors.joining(", "))
                            + ".");
        }
    }

    private static List<TechnologyModel> eligibleTechnologies(Game game, Player player, int maxPrerequisites) {
        return game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && tech.getFaction().isEmpty())
                .filter(tech -> TechnologyType.mainFour.contains(tech.getFirstType()))
                .filter(tech -> tech.getRequirements().orElse("").length() <= maxPrerequisites)
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .toList();
    }

    private static void sendTechPrompt(
            Player player, List<TechnologyModel> eligibleTechs, String message, boolean optional) {
        List<Button> buttons =
                new ArrayList<>(ListTechService.getTechButtons(new ArrayList<>(eligibleTechs), player, "free"));
        if (optional) {
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
