package ti4.discord.interactions.buttons.handlers.spending;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.EmergencyAppropriationsLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.PriorityRequisitionLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.SharedResourcesLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.WildlifePreservationLLButtonHandler;
import ti4.discord.interactions.buttons.handlers.explore.theodisi.LostLegciesExploreHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant.RevenantBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thrones.ThronesThroneHandler;
import ti4.discord.interactions.buttons.handlers.planet.IxthLegendaryButtonHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaust;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.thirtyk.ThirtyKExploreHooks;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
class SpendingButtonHandler {

    private static final String SPEND_PAGE_PREFIX = "spendPage_";
    private static final int MAX_BUTTONS_PER_MESSAGE = 25;

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.getFactionEmojiOrColor();
        String message;

        if (tgLoss > player.getCommodities()) {
            message = "You don't have " + tgLoss + " commodit" + (tgLoss == 1 ? "y" : "ies") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setCommodities(player.getCommodities() - tgLoss);
            player.addSpentThing("comm_" + tgLoss);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        Leader playerLeader = player.getLeader("keleresagent").orElse(null);
        if (playerLeader != null && !playerLeader.isExhausted()) {
            playerLeader.setExhausted(true);
            RevenantBreakthroughHandler.exhaustRevenantRisingForAttachedAgent(game, player, playerLeader);
            String messageText =
                    player.getRepresentation() + " exhausted " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);
        } else {
            game.removeStoredValue("keleresAgentTarget");
        }
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("reduceTG_")
    public static void reduceTG(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        if (tgLoss > player.getTg()) {
            String message = "You don't have " + StringHelper.pluralize(tgLoss, "trade good") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setTg(player.getTg() - tgLoss);
            player.increaseTgsSpentThisWindow(tgLoss);
        }
        if (tgLoss > player.getTg()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spend_")
    public static void spend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        PlanetExhaust.doAction(player, planetName, game);
        player.addSpentThing(planetName);
        ThirtyKExploreHooks.onPlanetExhausted(event, game, player, planetName);
        IxthLegendaryButtonHandler.onPlanetSpent(game, player, planetName, whatIsItFor);

        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        if (uH != null) {
            if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                Tile tile = game.getTileFromPlanet(planetName);
                String msg = player.getRepresentation() + " added 1 infantry to "
                        + Helper.getPlanetRepresentation(planetName, game) + " due to the _Arcane Citadel_.";
                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_polymorphism.png")
                    && FoWHelper.playerHasShipsInSystem(player, game.getTileFromPlanet(planetName))) {
                LostLegciesExploreHandler.offerPolymorphism(event, game, player, planetName);
            }
            if (uH.getTokenList().contains("attachment_facilitylogisticshub.png")) {
                String msg = player.getRepresentation() + " gained 1 commodity due to exhausting "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " while it had a _Logistics Hub Facility_.";
                player.setCommodities(player.getCommodities() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_facilityresearchlab.png")) {
                int amountThereNow = game.changeCommsOnPlanet(1, planetName);

                String msg =
                        player.getRepresentation() + " gained 1 trade good on the _Research Lab_ due to exhausting "
                                + Helper.getPlanetRepresentation(planetName, game)
                                + ". It now has " + StringHelper.pluralize(amountThereNow, "trade good") + " on it.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
            if ((Mapper.getPlanet(planetName).getTechSpecialties() != null
                            && !Mapper.getPlanet(planetName)
                                    .getTechSpecialties()
                                    .isEmpty())
                    || ButtonHelper.checkForTechSkips(game, planetName)) {
                String msg = player.getRepresentation()
                        + " due to your **Ancient Knowledge** ability, you may be eligible to receive a commodity here if you exhausted this planet ("
                        + planetName
                        + ") for its technology speciality.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm));
                buttons.add(Buttons.red("deleteButtons", "N/A"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji()
                                + " may have the opportunity to gain a commodity from their **Ancient Knowledge** ability due to exhausting a technology speciality planet.");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("resetSpend_")
    public static void resetSpend_(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRespend(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 1) {
            whatIsItFor = buttonID.split("_")[1];
        }
        showSpendButtons(
                event, player, game, whatIsItFor, Helper.buildSpentThingsMessage(player, game, whatIsItFor), 0);
    }

    @ButtonHandler("resetSpend")
    public static void resetSpend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRevote(player, game);
        EmergencyAppropriationsLLButtonHandler.clear(game, player);
        PriorityRequisitionLLButtonHandler.clear(game, player);
        SharedResourcesLLButtonHandler.clear(game, player);
        WildlifePreservationLLButtonHandler.clear(game, player);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.resetSpentThings();
        showSpendButtons(
                event, player, game, whatIsItFor, Helper.buildSpentThingsMessage(player, game, whatIsItFor), 0);
    }

    @ButtonHandler(SPEND_PAGE_PREFIX)
    public static void turnSpendPage(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pageArgs = buttonID.substring(SPEND_PAGE_PREFIX.length());
        int separator = pageArgs.lastIndexOf("_page");
        String whatIsItFor = pageArgs.substring(0, separator);
        int page = Integer.parseInt(pageArgs.substring(separator + "_page".length()));
        showSpendButtons(event, player, game, whatIsItFor, event.getMessage().getContentRaw(), page);
    }

    private static void showSpendButtons(
            ButtonInteractionEvent event, Player player, Game game, String whatIsItFor, String message, int page) {
        List<Button> spendButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor));
        List<Button> fixedButtons = new ArrayList<>();
        spendButtons.removeIf(button -> isResetSpendButton(button) && fixedButtons.add(button));
        for (Button button : buttonsOnMessage(event)) {
            if (isResetSpendButton(button)
                    || button.getCustomId() == null
                    || button.getCustomId().startsWith(SPEND_PAGE_PREFIX)
                    || Helper.doesListContainButtonID(spendButtons, button.getCustomId())
                    || Helper.doesListContainButtonID(fixedButtons, button.getCustomId())) {
                continue;
            }
            if ("spendBlacktfCapturedInfantry".equals(button.getCustomId())
                    && !TwilightsFallMonumentsButtonHandler.canSpendBlacktfCapturedInfantry(game, player)) {
                continue;
            }
            fixedButtons.add(button);
        }
        event.getMessage()
                .editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(
                        spendButtonPage(spendButtons, fixedButtons, whatIsItFor, page)))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<Button> buttonsOnMessage(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (ActionRow row : event.getMessage().getComponentTree().findAll(ActionRow.class)) {
            for (ActionRowChildComponentUnion component : row.getComponents()) {
                if (component instanceof Button button) {
                    buttons.add(button);
                }
            }
        }
        return buttons;
    }

    private static boolean isResetSpendButton(Button button) {
        return button.getCustomId() != null && button.getCustomId().startsWith("resetSpend");
    }

    private static List<Button> spendButtonPage(
            List<Button> spendButtons, List<Button> fixedButtons, String whatIsItFor, int page) {
        List<Button> paged = NewStuffHelper.paginateWithPinnedButtons(
                spendButtons, fixedButtons, SPEND_PAGE_PREFIX + whatIsItFor + "_", MAX_BUTTONS_PER_MESSAGE, page);
        if (!paged.isEmpty()) {
            return paged;
        }
        List<Button> truncated = new ArrayList<>(spendButtons);
        truncated.addAll(fixedButtons);
        return truncated.subList(0, Math.min(MAX_BUTTONS_PER_MESSAGE, truncated.size()));
    }

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        ThronesThroneHandler.clearSkarnathDiscount(game, player);
        PriorityRequisitionLLButtonHandler.clear(game, player);
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
