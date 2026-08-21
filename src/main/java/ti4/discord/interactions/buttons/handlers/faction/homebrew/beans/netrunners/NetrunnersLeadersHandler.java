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
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
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
                                "Research Without Spending a Token",
                                FactionEmojis.netrunners),
                        Buttons.gray(
                                player.factionButtonChecker() + "netrunnersCommanderTechnologySecondary_spend",
                                "Spend a Token Normally",
                                FactionEmojis.netrunners)));
    }

    @ButtonHandler("netrunnersCommanderTechnologySecondary_")
    public static void resolveCommanderTechnologySecondary(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (!player.hasLeaderUnlocked("netrunnerscommander")) return;
        String choice = buttonID.replace("netrunnersCommanderTechnologySecondary_", "");
        if (!"skip".equals(choice) && !"spend".equals(choice)) return;
        game.setStoredValue("netrunnersCommanderTechnologySecondary" + player.getFaction(), choice);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        if ("spend".equals(choice)) offerCommanderFreeTechnology(game, player);
        ListTechService.acquireATech(event, game, player, true, false, TechnologyType.mainFive, true);
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
                        "Exhaust " + planet,
                        FactionEmojis.netrunners))
                .toList();
        if (!buttons.isEmpty()) {
            buttons = new java.util.ArrayList<>(buttons);
            buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", because you spent a strategy token, you may exhaust a technology-specialty planet to research a technology of that color for free via Tek Mir-un, the Netrunners commander.",
                    buttons);
        }
    }

    @ButtonHandler("netrunnersCommanderPlanet_")
    public static void resolveCommanderPlanet(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("netrunnersCommanderPlanet_", "");
        PlanetModel model = Mapper.getPlanet(planet);
        if (!player.hasLeaderUnlocked("netrunnerscommander")
                || !player.hasPlanetReady(planet)
                || model == null
                || model.getTechSpecialties() == null) return;
        List<TechnologyModel> techs = model.getTechSpecialties().stream()
                .filter(NetrunnersLeadersHandler::isColoredSpecialty)
                .flatMap(specialty ->
                        ListTechService.getAllTechOfAType(game, specialty.toString(), player, false, true).stream())
                .distinct()
                .toList();
        if (techs.isEmpty()) return;
        player.exhaustPlanet(planet);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
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

    public static void offerAgentDiscount(Game game, Player researcher, String techId) {
        if (game == null || researcher == null || Mapper.getTech(techId) == null) return;
        for (Player netrunner : game.getRealPlayers()) {
            if (!netrunner.hasUnexhaustedLeader("netrunnersagent")) continue;
            int discount = (int) game.getRealPlayersExcludingThis(netrunner).stream()
                    .filter(player -> player.hasTech(techId))
                    .count();
            if (discount < 1) continue;
            MessageHelper.sendMessageToChannelWithButton(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentationUnfogged() + ", " + researcher.getRepresentation(false, true)
                            + " is paying to research " + Mapper.getTech(techId).getNameRepresentation()
                            + ". You may exhaust Zor No-ahn, the Netrunners agent, to reduce that cost by "
                            + discount + ".",
                    Buttons.gray(
                            netrunner.factionButtonChecker() + "netrunnersAgentDiscount_" + researcher.getFaction()
                                    + "|" + techId,
                            "Use Netrunners Agent",
                            FactionEmojis.netrunners));
        }
    }

    public static Button getAgentCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "netrunnersAgentInfo", "Netrunners Agent", FactionEmojis.netrunners);
    }

    @ButtonHandler("netrunnersAgentInfo")
    public static void showAgentInfo(ButtonInteractionEvent event, Player player) {
        if (!player.hasUnexhaustedLeader("netrunnersagent")) {
            return;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                "Zor No-ahn, the Netrunners agent, is offered automatically when a player pays to research a technology. The discount is based on the number of other players that have that technology.");
    }

    @ButtonHandler("netrunnersAgentDiscount_")
    public static void resolveAgentDiscount(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersAgentDiscount_", "").split("\\|", 2);
        if (parts.length != 2) return;
        Player researcher = game.getPlayerFromColorOrFaction(parts[0]);
        String techId = parts[1];
        if (researcher == null || Mapper.getTech(techId) == null || !netrunner.hasUnexhaustedLeader("netrunnersagent"))
            return;
        int discount = (int) game.getRealPlayersExcludingThis(netrunner).stream()
                .filter(player -> player.hasTech(techId))
                .count();
        if (discount < 1) return;
        ExhaustLeaderService.exhaustLeader(
                game, netrunner, netrunner.getLeader("netrunnersagent").orElseThrow());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                researcher.getCorrectChannel(),
                netrunner.getRepresentation() + " exhausted Zor No-ahn, the Netrunners agent, reducing "
                        + researcher.getRepresentation(false, true) + "'s technology cost by " + discount
                        + ". Apply that reduction while resolving the already-open technology payment.");
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
                        Mapper.getTech(tech).getName(),
                        FactionEmojis.netrunners))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no technology eligible for _Power Surge - Network Overload_. The hero was already purged.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the shared technology to return with _Power Surge - Network Overload_.",
                buttons);
    }

    @ButtonHandler("netrunnersHeroTech_")
    public static void resolveHeroTech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String techId = buttonID.replace("netrunnersHeroTech_", "");
        if (Mapper.getTech(techId) == null
                || !player.hasTech(techId)
                || game.getRealPlayersExcludingThis(player).stream().noneMatch(other -> other.hasTech(techId))) return;
        for (Player owner : game.getRealPlayers()) {
            if (owner.hasTech(techId)) owner.removeTech(techId);
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned "
                        + Mapper.getTech(techId).getNameRepresentation()
                        + " for every owner via _Power Surge - Network Overload_.");
        offerHeroTokenSourceSelection(game, player);
    }

    private static void offerHeroTokenSourceSelection(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .filter(other ->
                        player.getDebtTokenCount(other.getColor(), NetrunnersAbilitiesHandler.CONTROL_TOKEN_POOL) > 0)
                .filter(other -> other.getTechs().stream()
                        .map(Mapper::getTech)
                        .anyMatch(tech ->
                                tech != null && tech.getFaction().isEmpty() && !player.hasTech(tech.getAlias())))
                .map(other -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersHeroSource_" + other.getFaction(),
                        "Return " + other.getColorDisplayName() + " Token",
                        FactionEmojis.netrunners))
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
                        tech.getName(),
                        FactionEmojis.netrunners))
                .toList();
        if (buttons.isEmpty()) return;
        ButtonHelper.deleteMessage(event);
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
        player.setTacticalCC(player.getTacticalCC() + 1);
        player.addTech(techId);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned "
                        + source.getRepresentation(false, true) + "'s control token, gained 1 command token and "
                        + Mapper.getTech(techId).getNameRepresentation() + " via _Power Surge - Network Overload_.");
        offerHeroTokenSourceSelection(game, player);
    }
}
