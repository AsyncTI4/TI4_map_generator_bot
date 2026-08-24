package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NetrunnersLeadersHandler {
    public static boolean shouldChooseCommanderTechnologySecondary(Game game, Player player) {
        return game != null
                && player != null
                && player.hasLeaderUnlocked("netrunnerscommander")
                && game.getStoredValue("netrunnersCommanderTechnologySecondary" + player.getFaction())
                        .isEmpty();
    }

    public static boolean commanderSkipsTechnologySecondaryToken(Game game, Player player) {
        return game != null
                && player != null
                && player.hasLeaderUnlocked("netrunnerscommander")
                && "skip".equals(game.getStoredValue("netrunnersCommanderTechnologySecondary" + player.getFaction()));
    }

    public static void offerCommanderTechnologySecondary(Game game, Player player) {
        if (!shouldChooseCommanderTechnologySecondary(game, player)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", Tek Mir-un, the Netrunners commander, lets you choose whether to spend a strategy token for this Technology secondary.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "netrunnersCommanderTechnologySecondary_skip",
                                "Research Without Spending a Token"),
                        Buttons.gray(
                                player.factionButtonChecker() + "netrunnersCommanderTechnologySecondary_spend",
                                "Spend a Token Normally")));
    }

    @ButtonHandler("netrunnersCommanderTechnologySecondary_")
    public static void resolveCommanderTechnologySecondary(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (!player.hasLeaderUnlocked("netrunnerscommander")) return;
        String choice = buttonID.replace("netrunnersCommanderTechnologySecondary_", "");
        if (!"skip".equals(choice) && !"spend".equals(choice)) return;
        if ("spend".equals(choice)) {
            resolveCommanderTechnologySpend(game, player, event);
            return;
        }
        game.setStoredValue("netrunnersCommanderTechnologySecondary" + player.getFaction(), "skip");
        ButtonHelper.deleteMessage(event);
        ListTechService.acquireATech(event, game, player, true, false, TechnologyType.mainFive, true);
    }

    private static void resolveCommanderTechnologySpend(Game game, Player player, ButtonInteractionEvent event) {
        StrategyCardModel technology =
                game.getStrategyCardModelByName("technology").orElse(null);
        boolean used = ButtonHelperSCs.addUsedSCPlayer(event.getMessageId(), game, player);
        if (!used
                && technology != null
                && technology.usesAutomationForSCID("pok7technology")
                && !player.getFollowedSCs().contains(technology.getInitiative())) {
            int scNum = technology.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed **Technology**");
            }
            ReactionService.addReaction(event, game, player, ButtonHelperSCs.deductCC(game, player, scNum));
        }
        ButtonHelper.deleteMessage(event);
        offerCommanderFreeTechnology(game, player);
    }

    private static void offerCommanderFreeTechnology(Game game, Player player) {
        List<Button> buttons = player.getPlanets().stream()
                .filter(player::hasPlanetReady)
                .filter(planet -> {
                    PlanetModel model = Mapper.getPlanet(planet);
                    return model != null
                            && model.getTechSpecialties() != null
                            && model.getTechSpecialties().stream()
                                    .anyMatch(NetrunnersLeadersHandler::isColoredSpecialty);
                })
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersCommanderPlanet_" + planet,
                        "Exhaust " + Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", you have no ready planet with a colored technology specialty for Tek Mir-un, the Netrunners commander.");
            return;
        }
        buttons = new java.util.ArrayList<>(buttons);
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", because you spent a strategy token, you may exhaust a technology-specialty planet to research a technology of that color for free via Tek Mir-un, the Netrunners commander.",
                buttons);
    }

    @ButtonHandler("netrunnersCommanderPlanet_")
    public static void resolveCommanderPlanet(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("netrunnersCommanderPlanet_", "");
        PlanetModel model = Mapper.getPlanet(planet);
        if (!player.hasLeaderUnlocked("netrunnerscommander")
                || !player.hasPlanetReady(planet)
                || model == null
                || model.getTechSpecialties() == null) return;
        player.exhaustPlanet(planet);
        List<TechnologyModel> techs = new java.util.ArrayList<>(model.getTechSpecialties().stream()
                .filter(NetrunnersLeadersHandler::isColoredSpecialty)
                .flatMap(specialty ->
                        ListTechService.getAllTechOfAType(game, specialty.toString(), player, false, true).stream())
                .distinct()
                .toList());
        if (techs.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + ", no technology matching that specialty is currently researchable via Tek Mir-un, the Netrunners commander.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the free technology to research via Tek Mir-un, the Netrunners commander:",
                ListTechService.getTechButtons(techs, player, "free"));
    }

    private static boolean isColoredSpecialty(TechSpecialty specialty) {
        return specialty == TechSpecialty.BIOTIC
                || specialty == TechSpecialty.CYBERNETIC
                || specialty == TechSpecialty.PROPULSION
                || specialty == TechSpecialty.WARFARE;
    }

    public static Button getAgentDiscountButton(Game game, Player player, String techId, String payType) {
        int discount = getAgentDiscount(game, player, player, techId);
        if (discount < 1) return null;
        return Buttons.gray(
                player.factionButtonChecker() + "netrunnersAgentDiscount_" + player.getFaction() + "|" + techId + "|"
                        + payType,
                "Use Netrunners Agent (-" + discount + ")",
                FactionEmojis.netrunners);
    }

    public static Button getAgentCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "netrunnersAgentInfo",
                "Use Netrunners Agent",
                FactionEmojis.netrunners);
    }

    @ButtonHandler("netrunnersAgentInfo")
    public static void chooseAgentTarget(Game game, ButtonInteractionEvent event, Player player) {
        if (game == null || !player.hasUnexhaustedLeader("netrunnersagent")) return;
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + "netrunnersAgentTarget_" + target.getFaction(),
                        "Use Netrunners Agent On " + target.getFactionModel().getShortName(),
                        target.getFactionEmoji()))
                .toList();
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the player receiving Zor No-ahn, the Netrunners agent's technology discount.",
                buttons);
    }

    @ButtonHandler("netrunnersAgentTarget_")
    public static void chooseAgentTechnology(Game game, ButtonInteractionEvent event, Player player, String buttonID) {
        if (game == null || !player.hasUnexhaustedLeader("netrunnersagent")) return;
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("netrunnersAgentTarget_", ""));
        if (target == null || target == player) return;
        List<Button> buttons = target.getTechs().stream()
                .filter(techId -> Mapper.getTech(techId) != null)
                .map(techId -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersAgentDiscount_" + target.getFaction() + "|" + techId,
                        Mapper.getTech(techId).getName()))
                .toList();
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " has no eligible technology for that player.");
            return;
        }
        String message = player.getRepresentationUnfogged()
                + ", please choose the technology " + target.getRepresentation(false, true)
                + " is researching with Zor No-ahn, the Netrunners agent.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersAgentDiscount_" + target.getFaction() + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler("netrunnersAgentDiscount_")
    public static void resolveAgentDiscount(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        if (game == null || netrunner == null) return;
        String[] parts = buttonID.replace("netrunnersAgentDiscount_", "").split("\\|", 3);
        if (parts.length < 2) return;
        Player researcher = game.getPlayerFromColorOrFaction(parts[0]);
        String techId = parts[1];
        String payType = parts.length == 3 ? parts[2] : "res";
        if (researcher == null) return;
        List<Button> techButtons = researcher.getTechs().stream()
                .filter(candidate -> Mapper.getTech(candidate) != null)
                .map(candidate -> Buttons.green(
                        netrunner.factionButtonChecker() + "netrunnersAgentDiscount_" + researcher.getFaction() + "|"
                                + candidate,
                        Mapper.getTech(candidate).getName()))
                .toList();
        String message = netrunner.getRepresentationUnfogged()
                + ", please choose the technology " + researcher.getRepresentation(false, true)
                + " is researching with Zor No-ahn, the Netrunners agent.";
        String buttonPrefix =
                netrunner.factionButtonChecker() + "netrunnersAgentDiscount_" + researcher.getFaction() + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), techButtons, message, buttonPrefix, buttonID)) return;
        int discount = getAgentDiscount(game, netrunner, researcher, techId);
        if (!researcher.hasTech(techId) || discount < 1) return;
        ExhaustLeaderService.exhaustLeader(
                game, netrunner, netrunner.getLeader("netrunnersagent").orElseThrow());
        researcher.addSpentThing("netrunnersAgentDiscount_" + discount);
        if (researcher == netrunner) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
            event.getMessage()
                    .editMessage(Helper.buildSpentThingsMessage(researcher, game, payType))
                    .queue();
        } else {
            ButtonHelper.deleteMessage(event);
        }
        MessageHelper.sendMessageToChannel(
                researcher.getCorrectChannel(),
                netrunner.getRepresentation() + " exhausted Zor No-ahn, the Netrunners agent, reducing "
                        + researcher.getRepresentation(false, true) + "'s technology cost by " + discount
                        + ". The discount has been added to their spend summary.");
    }

    private static int getAgentDiscount(Game game, Player netrunner, Player researcher, String techId) {
        if (game == null
                || netrunner == null
                || researcher == null
                || Mapper.getTech(techId) == null
                || !netrunner.hasUnexhaustedLeader("netrunnersagent")) return 0;
        return (int) game.getRealPlayersExcludingThis(researcher).stream()
                .filter(player -> player.hasTech(techId))
                .count();
    }

    public static void offerHeroTechSelection(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        List<Button> buttons = player.getTechs().stream()
                .filter(tech -> Mapper.getTech(tech) != null)
                .filter(tech ->
                        game.getRealPlayersExcludingThis(player).stream().anyMatch(other -> other.hasTech(tech)))
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersHeroTech_" + tech,
                        Mapper.getTech(tech).getName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no technology eligible for _Power Surge - Network Overload_. The hero was already purged.");
            return;
        }
        String message = player.getRepresentationUnfogged()
                + ", please choose the shared technology to return with _Power Surge - Network Overload_.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersHeroTech_";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler("netrunnersHeroTech_")
    public static void resolveHeroTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = player.getTechs().stream()
                .filter(tech -> Mapper.getTech(tech) != null)
                .filter(tech ->
                        game.getRealPlayersExcludingThis(player).stream().anyMatch(other -> other.hasTech(tech)))
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersHeroTech_" + tech,
                        Mapper.getTech(tech).getName()))
                .toList();
        String message = player.getRepresentationUnfogged()
                + ", please choose the shared technology to return with _Power Surge - Network Overload_.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersHeroTech_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;
        String techId = buttonID.replace("netrunnersHeroTech_", "");
        if (Mapper.getTech(techId) == null
                || !player.hasTech(techId)
                || game.getRealPlayersExcludingThis(player).stream().noneMatch(other -> other.hasTech(techId))) return;
        for (Player owner : game.getRealPlayersExcludingThis(player)) {
            if (owner.hasTech(techId)) owner.removeTech(techId);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned "
                        + Mapper.getTech(techId).getNameRepresentation()
                        + " for every other owner via _Power Surge - Network Overload_.");
        offerHeroTokenSourceSelection(game, player);
    }

    private static void offerHeroTokenSourceSelection(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(other -> isEligibleHeroTokenSource(player, other))
                .map(other -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersHeroSource_" + other.getFaction(),
                        "Return " + other.getColorDisplayName() + " Token"))
                .toList();
        if (buttons.isEmpty()) {
            finishHero(game, player, null);
            return;
        }
        buttons = new java.util.ArrayList<>(buttons);
        buttons.add(Buttons.red(player.factionButtonChecker() + "netrunnersHeroDone", "Done Returning Tokens"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you may return a control token to gain 1 command token and a non-faction technology that player has.",
                buttons);
    }

    private static boolean isEligibleHeroTokenSource(Player player, Player source) {
        return player.getDebtTokenCount(source.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) > 0
                && source.getTechs().stream()
                        .map(Mapper::getTech)
                        .anyMatch(tech ->
                                tech != null && tech.getFaction().isEmpty() && !player.hasTech(tech.getAlias()));
    }

    @ButtonHandler("netrunnersHeroDone")
    public static void finishHero(Game game, Player player, ButtonInteractionEvent event) {
        if (game == null || player == null) {
            return;
        }
        if (event != null) ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("netrunnersHeroSource_")
    public static void chooseHeroTechnology(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player source = game.getPlayerFromColorOrFaction(buttonID.replace("netrunnersHeroSource_", ""));
        if (source == null
                || player.getDebtTokenCount(source.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) < 1)
            return;
        List<Button> buttons = source.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && tech.getFaction().isEmpty() && !player.hasTech(tech.getAlias()))
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersHeroGain_" + source.getFaction() + "_"
                                + tech.getAlias(),
                        tech.getName()))
                .toList();
        boolean hasAnotherSource = game.getRealPlayersExcludingThis(player).stream()
                .anyMatch(other -> other != source && isEligibleHeroTokenSource(player, other));
        if (buttons.isEmpty()) {
            if (hasAnotherSource) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
            } else {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }
        if (hasAnotherSource) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        } else {
            ButtonHelper.deleteMessage(event);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose the technology to gain:",
                buttons);
    }

    @ButtonHandler("netrunnersHeroGain_")
    public static void resolveHeroTechnology(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersHeroGain_", "").split("_", 2);
        if (parts.length != 2) return;
        Player source = game.getPlayerFromColorOrFaction(parts[0]);
        String techId = parts[1];
        if (source == null
                || !source.hasTech(techId)
                || Mapper.getTech(techId) == null
                || Mapper.getTech(techId).getFaction().isPresent()
                || player.hasTech(techId)
                || player.getDebtTokenCount(source.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) < 1)
            return;
        player.clearDebt(source, 1, NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL);
        player.addTech(techId);
        NetrunnersUnitsHandler.offerLegionDeploy(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned "
                        + source.getRepresentation(false, true) + "'s control token and gained "
                        + Mapper.getTech(techId).getNameRepresentation() + " via _Power Surge - Network Overload_.");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose where to gain 1 command token via _Power Surge - Network Overload_.",
                ButtonHelper.getGainCCButtons(player));
    }
}
