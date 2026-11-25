package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionTileHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.TileHelper;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.map.FractureService;
import ti4.service.relic.SendRelicService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

public class TheodisiAgendaResolver implements AgendaResolver {
    private static final String PEACE_SUMMIT_PLACE = "theodisiPeaceSummitPlace_";
    private static final String MINISTER_RELATIONS_PLACE = "theodisiMinisterRelationsPlace_";
    private static final String CULTURAL_REDISTRIBUTION = "theodisiCulturalRedistribution_";
    private static final String LEGISLATIVE_ANARCHY_DESTROY = "theodisiLegislativeAnarchyDestroy_";
    private static final String EXPLORATION_CHARTER_PLACE = "theodisiExplorationCharterPlace_";
    private static final String ACCULTURATION_PLACE = "theodisiAcculturationPlace_";
    private static final String DARK_INVASION_DESTROY = "theodisiDarkInvasionDestroy_";
    private static final String LIGHT_SPEED_DESTROY = "theodisiLightSpeedDestroy_";
    private static final String REELECTION_STARTING_UNITS = "theodisiReelectionStartingUnits";
    private static final String REELECTION_STRUCTURE = "theodisiReelectionStructure_";
    private static final String SEAT_OF_POWER = "theodisiSeatOfPower";
    private final String agendaId;

    public TheodisiAgendaResolver(String agendaId) {
        this.agendaId = agendaId;
    }

    @Override
    public String agendaId() {
        return agendaId;
    }

    public static boolean onAgendaRevealed(Game game, GenericInteractionCreateEvent event, String agendaId) {
        if ("culturalredistribution".equals(agendaId)
                && game.getRealPlayers().stream()
                        .noneMatch(player -> !player.getRelics().isEmpty())) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "_Cultural Redistribution_ was discarded because no player has a relic.");
            return true;
        }
        if ("acculturation".equals(agendaId)) {
            FactionModel faction = drawUnusedFaction(game);
            if (faction != null) {
                game.setStoredValue("theodisiAcculturationFaction", faction.getAlias());
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        game.getPing() + " _Acculturation_ drew the " + faction.getFactionName()
                                + " faction reference card.\n**Starting Units:** "
                                + Helper.getOrderedUnitListEmojis(faction.getStartingFleet(), true)
                                + "\n**Starting Technologies:** "
                                + getStartingTechnologySummary(faction));
            }
        }
        if (!"pirateuprising".equals(agendaId) && !"darkinvasion".equals(agendaId)) return false;
        if (FractureService.isFractureInPlay(game)) return false;
        Player speaker = game.getSpeaker();
        if (speaker == null || !FractureService.spawnFracture(event, game)) return false;
        FractureService.spawnIngressTokens(event, game, speaker, null);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                speaker.getRepresentationNoPing() + " brought The Fracture into play due to _"
                        + ("pirateuprising".equals(agendaId) ? "Pirate Uprising" : "Dark Invasion") + "_.");
        return false;
    }

    private static String getStartingTechnologySummary(FactionModel faction) {
        if (faction.getStartingTech() != null && !faction.getStartingTech().isEmpty()) {
            return faction.getStartingTech().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .map(TechnologyModel::getNameRepresentation)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("None");
        }
        if (faction.getStartingTechOptions() != null
                && !faction.getStartingTechOptions().isEmpty()) {
            String options = faction.getStartingTechOptions().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .map(TechnologyModel::getNameRepresentation)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("None");
            return "Choose " + faction.getStartingTechAmount() + ": " + options;
        }
        return "None";
    }

    public static void offerMinisterRelations(Game game, Player player, int scNum) {
        if (!IsPlayerElectedService.isPlayerElected(game, player, "ministerrelations")
                || !game.getStoredValue("theodisiMinisterRelationsExhausted_" + player.getFaction())
                        .isEmpty()) {
            return;
        }
        Player strategyCardOwner = game.getRealPlayers().stream()
                .filter(candidate -> candidate.getSCs().contains(scNum))
                .findFirst()
                .orElse(null);
        if (strategyCardOwner == null
                || strategyCardOwner == player
                || strategyCardOwner.getPlanets().isEmpty()) return;
        List<Button> buttons = new ArrayList<>(strategyCardOwner.getPlanets().stream()
                .filter(planet -> isEligibleMinisterRelationsPlanet(game, strategyCardOwner, planet))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + MINISTER_RELATIONS_PLACE + planet + "|" + scNum,
                        "Place infantry on " + Helper.getPlanetRepresentation(planet, game)))
                .toList());
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline Minister of Relations"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Minister of Relations_ to place 1 infantry into coexistence on a planet controlled by "
                        + strategyCardOwner.getRepresentationNoPing() + ".",
                buttons);
    }

    private static boolean isEligibleMinisterRelationsPlanet(Game game, Player strategyCardOwner, String planet) {
        return game.getUnitHolderFromPlanet(planet) != null
                && strategyCardOwner.getPlanets().contains(planet)
                && game.getUnitHolderFromPlanet(planet).hasGroundForces(strategyCardOwner);
    }

    public static void grantUnchosenTradeStrategyCard(Game game) {
        if (!ButtonHelper.isLawInPlay(game, "freemarketcapitalism")) return;
        int trade = game.getSCList().stream()
                .filter(sc -> game.getStrategyCardModelByInitiative(sc)
                        .map(model -> model.usesAutomationForSCID("pok5trade"))
                        .orElse(false))
                .findFirst()
                .orElse(-1);
        if (trade < 0
                || game.getRealPlayers().stream()
                        .anyMatch(player -> player.getSCs().contains(trade))) return;
        List<Player> order = ti4.helpers.Helper.getSpeakerOrFullPriorityOrder(game);
        if (order.isEmpty()) return;
        Player recipient = order.getLast();
        recipient.addSC(trade);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                recipient.getRepresentationNoPing() + " gained **"
                        + game.getStrategyCardModelByInitiative(trade)
                                .orElseThrow()
                                .getName()
                        + "** as an additional strategy card due to _Free Market Capitalism_.");
    }

    public static void resolveMinisterFools(Game game, Player player) {
        if (!IsPlayerElectedService.isPlayerElected(game, player, "ministerfools")) return;
        int result = new Die(0).getResult();
        String outcome;
        if (result == 1) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), player.getRepresentation(), ButtonHelper.getLoseCCButtons(player));
            outcome = "return 1 command token to your reinforcements";
        } else if (result <= 4) {
            ActionCardHelper.drawActionCards(player, 1);
            outcome = "drew 1 action card";
        } else if (result <= 7) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", choose where to gain 1 command token from _Minister of Fools_.",
                    ButtonHelper.getGainCCButtons(player));
            outcome = "may gain 1 command token";
        } else {
            List<TechnologyModel> techs = new ArrayList<>(TechnologyType.mainFour.stream()
                    .flatMap(type ->
                            ListTechService.getAllTechOfAType(game, type.toString(), player, false, true).stream())
                    .toList());
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", choose a technology to research for free with _Minister of Fools_.",
                    ListTechService.getTechButtons(techs, player, "free"));
            outcome = "may research 1 technology for free";
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " rolled " + result + " for _Minister of Fools_ and " + outcome
                        + ".");
    }

    @ButtonHandler(MINISTER_RELATIONS_PLACE)
    public static void resolveMinisterRelations(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!IsPlayerElectedService.isPlayerElected(game, player, "ministerrelations")
                || !game.getStoredValue("theodisiMinisterRelationsExhausted_" + player.getFaction())
                        .isEmpty()) {
            return;
        }
        String[] buttonParts =
                buttonID.substring(MINISTER_RELATIONS_PLACE.length()).split("\\|", 2);
        if (buttonParts.length != 2) return;
        String planet = buttonParts[0];
        int scNum;
        try {
            scNum = Integer.parseInt(buttonParts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileFromPlanet(planet);
        Player strategyCardOwner = game.getRealPlayers().stream()
                .filter(candidate -> candidate.getSCs().contains(scNum))
                .findFirst()
                .orElse(null);
        if (tile == null
                || strategyCardOwner == null
                || !isEligibleMinisterRelationsPlanet(game, strategyCardOwner, planet)) {
            return;
        }
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        if (coexistenceFlag.isEmpty()) game.removeStoredValue("coexistFlag");
        else game.setStoredValue("coexistFlag", coexistenceFlag);
        game.setStoredValue("theodisiMinisterRelationsExhausted_" + player.getFaction(), "true");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Minister of Relations_ and placed 1 infantry into coexistence on " + planet
                        + ".");
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        switch (agendaId) {
            case "districtredraw" -> resolveDistrictRedraw(game, winner);
            case "reappropriation" -> resolveReappropriation(game, event, winner);
            case "peacesummit" -> resolvePeaceSummit(game, event, winner);
            case "culturalredistribution" -> resolveCulturalRedistribution(game, winner);
            case "ministercontrol", "ministerrelations" -> {}
            case "ministerthrone" -> resolveMinisterThrone(game, winner);
            case "ministerfools" -> game.setStoredValue("theodisiMinisterFools", "true");
            case "darkinvasion" -> resolveDarkInvasion(game, winner);
            case "fleetpoolingenforcement" -> resolveFleetPooling(game, winner);
            case "lightspeedregulations" -> resolveLightSpeedRegulations(game, winner);
            case "freemarketcapitalism" -> resolveFreeMarketCapitalism(game, winner);
            case "legislativeanarchy" -> promptLegislativeAnarchy(game, winner);
            case "reelectioncampaign" -> promptReelectionCampaign(game, winner);
            case "falseprophets" -> promptFalseProphets(game, winner);
            case "opensourcerobotics" -> resolveOpenSourceRobotics(game, winner);
            case "explorationcharter" -> resolveExplorationCharter(game, event, winner);
            case "privateequity" -> resolvePrivateEquity(game, winner);
            case "acculturation" -> promptAcculturation(game, winner);
            case "pirateuprising" -> resolvePirateUprising(game, event, winner);
            default -> throw new IllegalStateException("Unexpected Theodisi agenda: " + agendaId);
        }
    }

    private static void resolveDistrictRedraw(Game game, String winner) {
        List<Player> affected = AgendaHelper.getLosingVoters(winner, game);
        for (Player player : affected) {
            if ("for".equalsIgnoreCase(winner)) {
                ActionCardHelper.drawActionCards(player, 2);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + ", gain 1 command token due to _District Redraw_.",
                        ButtonHelper.getGainCCButtons(player));
            }
        }
        String effect = "for".equalsIgnoreCase(winner) ? "drew 2 action cards" : "may gain 1 command token";
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "_District Redraw_ resolved: each affected voter " + effect + ".");
    }

    private static void resolveReappropriation(Game game, ButtonInteractionEvent event, String winner) {
        Player elected = game.getPlayerFromColorOrFaction(winner);
        if (elected == null) return;
        ActionCardHelper.discardRandomAC(event, game, elected, elected.getAcCount());
        ActionCardHelper.drawActionCards(elected, Math.max(0, 5 - elected.getAcCount()));
        MessageHelper.sendMessageToChannel(
                elected.getCorrectChannel(),
                elected.getRepresentationNoPing() + " resolved _Emergency Reappropriation_.");
    }

    private static void resolvePeaceSummit(Game game, ButtonInteractionEvent event, String planet) {
        Player controller = game.getRealPlayers().stream()
                .filter(player -> player.getPlanets().contains(planet))
                .findFirst()
                .orElse(null);
        Tile tile = game.getTileFromPlanet(planet);
        if (controller == null || tile == null) return;
        AddUnitService.addUnits(event, tile, game, controller.getColor(), "3 inf " + planet);
        for (Player player : game.getRealPlayersExcludingThis(controller)) {
            List<Button> buttons = List.of(
                    Buttons.green(
                            player.factionButtonChecker() + PEACE_SUMMIT_PLACE + planet,
                            "Place 1 infantry on " + Helper.getPlanetRepresentation(planet, game)),
                    Buttons.DECLINE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you may place 1 infantry into coexistence on " + planet
                            + " due to _Peace Summit_.",
                    buttons);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                controller.getRepresentationNoPing() + " placed 3 infantry on " + planet + ".");
    }

    @ButtonHandler(PEACE_SUMMIT_PLACE)
    public static void placePeaceSummitInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(PEACE_SUMMIT_PLACE.length());
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) return;
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        if (coexistenceFlag.isEmpty()) game.removeStoredValue("coexistFlag");
        else game.setStoredValue("coexistFlag", coexistenceFlag);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on " + planet + ".");
    }

    @ButtonHandler("theodisiMinisterControl")
    public static void resolveMinisterControl(ButtonInteractionEvent event, Game game, Player player) {
        if (!IsPlayerElectedService.isPlayerElected(game, player, "ministercontrol")
                || !game.removeLaw("ministercontrol")) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded _Minister of Control_ to gain 3 command tokens.",
                ButtonHelper.getGainCCButtons(player));
    }

    private static void resolveCulturalRedistribution(Game game, String relic) {
        Player owner = game.getRealPlayers().stream()
                .filter(player -> player.getRelics().contains(relic))
                .findFirst()
                .orElse(null);
        List<Player> fewest = AgendaHelper.getPlayersWithLeastPoints(game);
        if (owner == null || fewest.isEmpty()) return;
        List<Button> buttons = fewest.stream()
                .filter(player -> player != owner)
                .map(player -> Buttons.green(
                        owner.factionButtonChecker() + CULTURAL_REDISTRIBUTION + relic + "|" + player.getFaction(),
                        "Give to " + player.getFactionNameOrColor()))
                .toList();
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + ", choose a lowest-scoring player to receive _" + relic
                        + "_ due to _Cultural Redistribution_.",
                buttons);
    }

    @ButtonHandler(CULTURAL_REDISTRIBUTION)
    public static void resolveCulturalRedistributionTransfer(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload = buttonID.substring(CULTURAL_REDISTRIBUTION.length()).split("\\|", 2);
        if (payload.length != 2 || !owner.getRelics().contains(payload[0])) return;
        Player recipient = game.getPlayerFromColorOrFaction(payload[1]);
        if (recipient == null || !AgendaHelper.getPlayersWithLeastPoints(game).contains(recipient)) return;
        SendRelicService.handleSendRelic(event, game, owner, recipient, payload[0]);
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveMinisterThrone(Game game, String planet) {
        var planetHolder = game.getUnitHolderFromPlanet(planet);
        if (planetHolder == null) return;
        planetHolder.addToken("attachment_ministerthrone.png");
        game.setStoredValue("theodisiMinisterThronePlanet", planet);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "_Minister of the Throne_ is attached to " + planet
                        + ". Its controller gains the Seat of Power ability card.");
    }

    public static boolean hasSeatOfPower(Game game, Player player) {
        return player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(java.util.Objects::nonNull)
                .anyMatch(planet -> planet.getTokenList().contains("attachment_ministerthrone.png"));
    }

    public static Button getSeatOfPowerButton(Game game, Player player) {
        if (!hasSeatOfPower(game, player)
                || !game.getStoredValue("theodisiSeatOfPowerExhausted_" + player.getFaction())
                        .isEmpty()) return null;
        return Buttons.green(player.factionButtonChecker() + SEAT_OF_POWER, "Exhaust Seat of Power");
    }

    @ButtonHandler(SEAT_OF_POWER)
    public static void exhaustSeatOfPower(ButtonInteractionEvent event, Game game, Player player) {
        if (getSeatOfPowerButton(game, player) == null) return;
        game.setStoredValue("theodisiSeatOfPowerExhausted_" + player.getFaction(), "true");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + " drew 1 secret objective with _Seat of Power_. Discard 1 unscored secret objective.",
                SecretObjectiveHelper.getSODiscardButtonsWithSuffix(player, "redraw"));
    }

    private static void resolveDarkInvasion(Game game, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            game.setStoredValue("theodisiDarkInvasion", "true");
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "_Dark Invasion_ is in effect: home systems contain an ingress token.");
            return;
        }
        for (Player player : game.getRealPlayers()) offerShipDestruction(game, player, DARK_INVASION_DESTROY, false);
    }

    private static void resolveFleetPooling(Game game, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            game.setStoredValue("theodisiFleetPooling", "true");
            return;
        }
        for (Player player : game.getRealPlayers()) {
            int destroyed = 0;
            for (Tile tile : game.getTileMap().values()) {
                int fighters =
                        tile.getSpaceUnitHolder().getUnitCount(ti4.helpers.Units.UnitType.Fighter, player.getColor());
                if (fighters == 0) continue;
                int amount = (fighters + 1) / 2;
                DestroyUnitService.destroyUnits(null, tile, game, player.getColor(), amount + " ff", false);
                destroyed += amount;
            }
            if (destroyed > 0) player.gainTG(destroyed, true);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " destroyed " + destroyed
                            + " fighter" + (destroyed == 1 ? "" : "s")
                            + " and gained " + destroyed + " trade good" + (destroyed == 1 ? "" : "s")
                            + " due to _Fleet Pooling Enforcement_.");
        }
    }

    private static void resolveLightSpeedRegulations(Game game, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            return;
        }
        for (Player player : game.getRealPlayers()) offerShipDestruction(game, player, LIGHT_SPEED_DESTROY, true);
    }

    private static void offerShipDestruction(Game game, Player player, String prefix, boolean moveTwo) {
        List<DestroyableShip> ships = getDestroyableShips(game, player, moveTwo);
        int remaining = Math.min(3, ships.size());
        if (remaining == 0) return;
        game.setStoredValue(prefix + player.getFaction(), Integer.toString(remaining));
        sendShipDestructionButtons(game, player, prefix, moveTwo, remaining);
    }

    private static void sendShipDestructionButtons(
            Game game, Player player, String prefix, boolean moveTwo, int remaining) {
        List<Button> buttons = getDestroyableShips(game, player, moveTwo).stream()
                .distinct()
                .map(ship -> Buttons.red(
                        player.factionButtonChecker() + prefix + ship.tile().getPosition() + "|"
                                + ship.key().unitType().name(),
                        "Destroy " + ship.key().humanReadableName() + " in "
                                + ship.tile().getPosition()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", destroy " + remaining
                        + " eligible non-fighter ship" + (remaining == 1 ? "" : "s") + " due to _"
                        + (moveTwo ? "Light Speed Regulations" : "Dark Invasion") + "_.",
                buttons);
    }

    private static List<DestroyableShip> getDestroyableShips(Game game, Player player, boolean moveTwo) {
        List<DestroyableShip> ships = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!moveTwo && tile != player.getHomeSystemTile()) continue;
            for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
                var model = player.getUnitFromUnitKey(key);
                if (model == null || !model.isNonFighterShip() || (moveTwo && model.getMoveValue() < 2)) continue;
                for (int i = 0; i < tile.getSpaceUnitHolder().getUnitCount(key); i++)
                    ships.add(new DestroyableShip(tile, key));
            }
        }
        return ships;
    }

    private record DestroyableShip(Tile tile, UnitKey key) {}

    @ButtonHandler(DARK_INVASION_DESTROY)
    public static void destroyDarkInvasionShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        destroyAgendaShip(event, game, player, buttonID, DARK_INVASION_DESTROY, false);
    }

    @ButtonHandler(LIGHT_SPEED_DESTROY)
    public static void destroyLightSpeedShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        destroyAgendaShip(event, game, player, buttonID, LIGHT_SPEED_DESTROY, true);
    }

    private static void destroyAgendaShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, String prefix, boolean moveTwo) {
        String[] values = buttonID.substring(prefix.length()).split("\\|", 2);
        if (values.length != 2) return;
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(prefix + player.getFaction()));
        } catch (NumberFormatException exception) {
            return;
        }
        Tile tile = game.getTileByPosition(values[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(values[1]);
        } catch (IllegalArgumentException exception) {
            return;
        }
        UnitKey key = new UnitKey(type, player.getColorID());
        var model = player.getUnitFromUnitKey(key);
        if (tile == null
                || model == null
                || !model.isNonFighterShip()
                || (moveTwo && model.getMoveValue() < 2)
                || (!moveTwo && tile != player.getHomeSystemTile())
                || tile.getSpaceUnitHolder().getUnitCount(key) == 0) return;
        DestroyUnitService.destroyUnit(event, tile, game, key, 1, tile.getSpaceUnitHolder(), false);
        ButtonHelper.deleteMessage(event);
        remaining--;
        if (remaining <= 0 || getDestroyableShips(game, player, moveTwo).isEmpty()) {
            game.removeStoredValue(prefix + player.getFaction());
        } else {
            game.setStoredValue(prefix + player.getFaction(), Integer.toString(remaining));
            sendShipDestructionButtons(game, player, prefix, moveTwo, remaining);
        }
    }

    private static void resolveFreeMarketCapitalism(Game game, String winner) {
        if ("for".equalsIgnoreCase(winner)) {
            game.setStoredValue("theodisiFreeMarketCapitalism", "true");
            return;
        }
        for (Player player : game.getRealPlayers()) {
            player.setTg(3);
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "_Free Market Capitalism_ set every player's trade goods to 3.");
    }

    private static void promptLegislativeAnarchy(Game game, String winner) {
        List<Player> affected = AgendaHelper.getLosingVoters(winner, game);
        for (Player player : affected) {
            List<Button> buttons = getLegislativeAnarchyButtons(game, player);
            if (buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing()
                                + " has no eligible unit to destroy for _Legislative Anarchy_.");
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", choose 1 other player's unit to destroy due to _Legislative Anarchy_.",
                    buttons);
        }
    }

    private static List<Button> getLegislativeAnarchyButtons(Game game, Player chooser) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (game.isFowMode()
                    && !FoWHelper.getTilePositionsToShow(game, chooser).contains(tile.getPosition())) {
                continue;
            }
            boolean adjacentToChooser = Adjacency.of(game, chooser).forEffects().either(tile.getPosition()).stream()
                    .map(game::getTileByPosition)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(adjacent -> FoWHelper.playerHasUnitsInSystem(chooser, adjacent));
            if (!adjacentToChooser) continue;
            for (var holder : tile.getUnitHolders().values()) {
                for (UnitKey key : holder.getUnits().keySet()) {
                    Player owner = game.getPlayerFromColorOrFaction(key.colorID());
                    if (owner == null || owner == chooser || holder.getUnitCount(key) == 0) continue;
                    buttons.add(Buttons.red(
                            chooser.factionButtonChecker() + LEGISLATIVE_ANARCHY_DESTROY + tile.getPosition() + "|"
                                    + holder.getName() + "|" + key.unitType().name() + "|" + key.colorID(),
                            "Destroy " + owner.getFactionNameOrColor() + " " + key.humanReadableName() + " in "
                                    + tile.getRepresentationForButtons(game, chooser)));
                }
            }
        }
        return buttons;
    }

    @ButtonHandler(LEGISLATIVE_ANARCHY_DESTROY)
    public static void resolveLegislativeAnarchy(
            ButtonInteractionEvent event, Game game, Player chooser, String buttonID) {
        String[] values =
                buttonID.substring(LEGISLATIVE_ANARCHY_DESTROY.length()).split("\\|", 4);
        if (values.length != 4) return;
        Tile tile = game.getTileByPosition(values[0]);
        if (tile == null) return;
        var holder = tile.getUnitHolders().get(values[1]);
        UnitType unitType;
        try {
            unitType = UnitType.valueOf(values[2]);
        } catch (IllegalArgumentException exception) {
            return;
        }
        UnitKey key = new UnitKey(unitType, values[3]);
        if (holder == null || key == null || holder.getUnitCount(key) == 0) return;
        Player owner = game.getPlayerFromColorOrFaction(key.colorID());
        if (owner == null || owner == chooser) return;
        boolean adjacentToChooser = Adjacency.of(game, chooser).forEffects().either(tile.getPosition()).stream()
                .map(game::getTileByPosition)
                .filter(java.util.Objects::nonNull)
                .anyMatch(adjacent -> FoWHelper.playerHasUnitsInSystem(chooser, adjacent));
        if (!adjacentToChooser) return;
        DestroyUnitService.destroyUnit(event, tile, game, key, 1, holder, false);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                chooser.getRepresentationNoPing() + " destroyed 1 " + owner.getFactionEmoji() + " "
                        + key.humanReadableName() + " due to _Legislative Anarchy_.");
    }

    private static void promptReelectionCampaign(Game game, String winner) {
        for (Player player : AgendaHelper.getWinningVoters(winner, game)) {
            if ("for".equalsIgnoreCase(winner)) {
                offerReelectionStartingUnits(game, player);
            } else {
                offerReelectionStructures(game, player);
            }
        }
    }

    private static void offerReelectionStartingUnits(Game game, Player player) {
        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null || FoWHelper.otherPlayersHaveShipsInSystem(player, homeSystem, game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " cannot place starting units from _Re-election Campaign_ because their home system contains another player's ships.");
            return;
        }
        String startingUnits =
                getNonStructureStartingUnits(player.getFactionModel().getStartingFleet());
        if (startingUnits.isBlank()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may place your starting ships and ground forces in your home system due to _Re-election Campaign_ (reinforcement limits apply).",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + REELECTION_STARTING_UNITS, "Place starting units"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(REELECTION_STARTING_UNITS)
    public static void placeReelectionStartingUnits(ButtonInteractionEvent event, Game game, Player player) {
        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null || FoWHelper.otherPlayersHaveShipsInSystem(player, homeSystem, game)) return;
        String startingUnits =
                getNonStructureStartingUnits(player.getFactionModel().getStartingFleet());
        if (startingUnits.isBlank()) return;
        AddUnitService.addUnitsToDefaultLocations(event, homeSystem, game, player.getColor(), startingUnits);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + startingUnits
                        + " in their home system due to _Re-election Campaign_.");
    }

    private static void offerReelectionStructures(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REELECTION_STRUCTURE + planet + "|pds",
                    "Place PDS on " + Helper.getPlanetRepresentation(planet, game)));
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REELECTION_STRUCTURE + planet + "|spacedock",
                    "Place space dock on " + Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) return;
        game.setStoredValue(REELECTION_STRUCTURE + player.getFaction(), "2");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", place up to 2 PDS or space docks from your reinforcements on planets you control due to _Re-election Campaign_.",
                buttons);
    }

    @ButtonHandler(REELECTION_STRUCTURE)
    public static void placeReelectionStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(REELECTION_STRUCTURE.length()).split("\\|", 2);
        if (values.length != 2 || !player.getPlanets().contains(values[0])) return;
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(REELECTION_STRUCTURE + player.getFaction()));
        } catch (NumberFormatException exception) {
            return;
        }
        if (remaining <= 0) return;
        Tile tile = game.getTileFromPlanet(values[0]);
        if (tile == null || (!"pds".equals(values[1]) && !"spacedock".equals(values[1]))) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + values[1] + " " + values[0]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 "
                        + ("pds".equals(values[1]) ? "PDS" : "space dock") + " on " + values[0]
                        + " due to _Re-election Campaign_.");
        remaining--;
        if (remaining == 0) {
            game.removeStoredValue(REELECTION_STRUCTURE + player.getFaction());
            ButtonHelper.deleteMessage(event);
        } else {
            game.setStoredValue(REELECTION_STRUCTURE + player.getFaction(), Integer.toString(remaining));
        }
    }

    private static void promptFalseProphets(Game game, String leaderType) {
        List<String> usedFactions = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            String leader = drawUnusedLeader(game, leaderType, usedFactions);
            if (leader == null) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + ", there was no unused " + leaderType
                                + " available for _False Prophets_.");
                continue;
            }
            LeaderModel model = Mapper.getLeader(leader);
            if (model != null) usedFactions.add(model.getFaction());
            if ("commander".equalsIgnoreCase(leaderType) && model != null) {
                String alliance = model.getFaction() + "_alliance";
                if (Mapper.getPromissoryNote(alliance) != null) {
                    player.setPromissoryNote(alliance);
                    player.addPromissoryNoteToPlayArea(alliance);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationNoPing() + " gained the " + model.getFaction()
                                    + " Alliance card from _False Prophets_.");
                    continue;
                }
            }
            player.addLeader(leader);
            if ("commander".equalsIgnoreCase(leaderType) || "hero".equalsIgnoreCase(leaderType)) {
                UnlockLeaderService.unlockLeader(
                        leader,
                        game,
                        player,
                        player.getRepresentationNoPing() + " gained and unlocked " + model.getNameRepresentation()
                                + " from _False Prophets_.");
                continue;
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained " + model.getNameRepresentation()
                            + " from _False Prophets_.");
        }
    }

    private static String drawUnusedLeader(Game game, String leaderType, List<String> usedFactions) {
        List<FactionModel> factions = new ArrayList<>(Mapper.getFactionsValues());
        Collections.shuffle(factions);
        for (FactionModel faction : factions) {
            if (!isEnabledFactionSource(game, faction.getSource())
                    || game.getFactions().contains(faction.getAlias())
                    || usedFactions.contains(faction.getAlias())) continue;
            for (String leaderId : faction.getLeaders()) {
                LeaderModel leader = Mapper.getLeader(leaderId);
                if (leader != null
                        && leaderType.equalsIgnoreCase(leader.getType())
                        && ti4.helpers.Helper.getPlayerFromLeader(game, leaderId) == null) {
                    return leaderId;
                }
            }
        }
        return null;
    }

    private static void resolveOpenSourceRobotics(Game game, String unitType) {
        List<FactionModel> factions = new ArrayList<>(Mapper.getFactionsValues());
        Collections.shuffle(factions);
        FactionModel faction = null;
        UnitModel profile = null;
        for (FactionModel candidate : factions) {
            if (!isEnabledFactionSource(game, candidate.getSource())
                    || game.getFactions().contains(candidate.getAlias())) {
                continue;
            }
            UnitModel candidateProfile = getOpenSourceProfile(candidate, unitType);
            if (candidateProfile == null) continue;
            faction = candidate;
            profile = candidateProfile;
            break;
        }
        if (faction == null || profile == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "_Open Source Robotics_ found no unused faction with a faction-specific " + unitType + ".");
            return;
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "_Open Source Robotics_ placed the " + faction.getFactionName() + " reference card on the law. Each "
                        + unitType + " gains the unit abilities and ability text of " + profile.getName() + ".");
        game.setStoredValue("theodisiOpenSourceRoboticsUnit", profile.getId());
    }

    private static UnitModel getOpenSourceProfile(FactionModel faction, String unitType) {
        return faction.getUnits().stream()
                .map(Mapper::getUnit)
                .filter(java.util.Objects::nonNull)
                .filter(unit -> faction.getAlias().equals(unit.getFaction().orElse(null)))
                .filter(unit -> isMatchingUnitType(unit, unitType))
                .map(unit -> getHighestUnitUpgrade(unit, faction.getAlias()))
                .findFirst()
                .orElse(null);
    }

    private static UnitModel getHighestUnitUpgrade(UnitModel unit, String faction) {
        UnitModel highest = unit;
        while (true) {
            UnitModel upgrade =
                    highest.getUpgradesToUnitId().map(Mapper::getUnit).orElse(null);
            if (upgrade == null || !faction.equals(upgrade.getFaction().orElse(null))) {
                return highest;
            }
            highest = upgrade;
        }
    }

    private static boolean isMatchingUnitType(ti4.model.UnitModel unit, String unitType) {
        return unit.getBaseType().equalsIgnoreCase(unitType)
                || unit.getAsyncId().equalsIgnoreCase(unitType)
                || unit.getUnitType().humanReadableName().equalsIgnoreCase(unitType);
    }

    private static void resolveExplorationCharter(Game game, ButtonInteractionEvent event, String winner) {
        Player elected = game.getPlayerFromColorOrFaction(winner);
        if (elected == null) return;
        List<String> tiles = OblivionTileHelper.drawUnusedTiles(game, null, 1);
        if (tiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    elected.getCorrectChannel(),
                    elected.getRepresentationNoPing()
                            + " cannot resolve _Exploration Charter_ because no unused blue-backed system tile remains.");
            return;
        }
        String tileId = tiles.getFirst();
        List<Button> buttons = OblivionTileHelper.getPlacementButtons(
                game, elected, tileId, elected.factionButtonChecker() + EXPLORATION_CHARTER_PLACE);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    elected.getCorrectChannel(),
                    elected.getRepresentationNoPing()
                            + " drew " + TileHelper.getTileById(tileId).getName()
                            + " for _Exploration Charter_, but there is no legal edge position adjacent to two systems.");
            return;
        }
        game.setStoredValue("theodisiExplorationCharter_" + elected.getFaction(), tileId);
        String prefix = elected.factionButtonChecker() + EXPLORATION_CHARTER_PLACE + tileId + "_";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                elected.getCorrectChannel(),
                elected.getRepresentationNoPing() + ", choose where to place "
                        + TileHelper.getTileById(tileId).getName() + " due to _Exploration Charter_.",
                List.of(TileHelper.getTileById(tileId).getRepresentationEmbed(false)),
                NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    @ButtonHandler(EXPLORATION_CHARTER_PLACE)
    public static void placeExplorationCharterTile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String data = buttonID.substring(EXPLORATION_CHARTER_PLACE.length());
        int split = data.lastIndexOf('_');
        if (split <= 0) return;
        String tileId = data.substring(0, split);
        String position = data.substring(split + 1);
        String key = "theodisiExplorationCharter_" + player.getFaction();
        if (!tileId.equals(game.getStoredValue(key))) return;
        List<Button> buttons = OblivionTileHelper.getPlacementButtons(
                game, player, tileId, player.factionButtonChecker() + EXPLORATION_CHARTER_PLACE);
        String prefix = player.factionButtonChecker() + EXPLORATION_CHARTER_PLACE + tileId + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                player.getRepresentationNoPing() + ", choose an edge position for _Exploration Charter_.",
                prefix,
                buttonID)) {
            return;
        }
        Tile tile = OblivionTileHelper.placeTile(game, tileId, position);
        if (tile == null) return;
        game.removeStoredValue(key);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + tile.getRepresentationForButtons(game, player)
                        + " due to _Exploration Charter_.");
    }

    private static void resolvePrivateEquity(Game game, String winner) {
        FactionModel faction = drawUnusedFaction(game);
        if (faction == null) return;
        if ("for".equalsIgnoreCase(winner)) {
            game.setStoredValue("theodisiPrivateEquity", Integer.toString(faction.getCommodities()));
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "_Private Equity_ placed the " + faction.getFactionName()
                            + " reference card on the law. Each player's commodity value increased by "
                            + faction.getCommodities() + ".");
        } else {
            for (Player player : game.getRealPlayers()) {
                player.gainTG(faction.getCommodities(), true);
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    "_Private Equity_ gave every player " + faction.getCommodities() + " trade goods from the "
                            + faction.getFactionName() + " reference card.");
        }
    }

    private static FactionModel drawUnusedFaction(Game game) {
        List<FactionModel> factions = new ArrayList<>(Mapper.getFactionsValues());
        Collections.shuffle(factions);
        return factions.stream()
                .filter(faction -> isEnabledFactionSource(game, faction.getSource()))
                .filter(faction -> !game.getFactions().contains(faction.getAlias()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isEnabledFactionSource(Game game, ComponentSource source) {
        if (game.getDraftSystemSettingsUnsafe() != null || game.getDraftSystemSettingsJson() != null) {
            return game.initializeDraftSystemSettings()
                    .getSourceSettings()
                    .getFactionSources()
                    .contains(source);
        }
        if (source == ComponentSource.base
                || source == ComponentSource.pok
                || source == ComponentSource.codex1
                || source == ComponentSource.codex2
                || source == ComponentSource.codex3
                || source == ComponentSource.codex4) {
            return true;
        }
        if (game.isFrankenGame() && source == ComponentSource.wftv) {
            return game.initializeFrankenSettings().isWftvEnabled();
        }
        if (game.isFrankenGame() && source == ComponentSource.theodisi) {
            return game.initializeFrankenSettings().isLostLegaciesEnabled();
        }
        if (source == ComponentSource.ds) return game.isDiscordantStarsMode();
        if (source == ComponentSource.blue_reverie) return game.isBlueReverieMode();
        if (source == ComponentSource.thunders_edge) return game.isThundersEdge();
        if (source == ComponentSource.twilights_fall) return game.isTwilightsFallMode();
        return game.getRealPlayers().stream()
                .map(Player::getFactionModel)
                .filter(java.util.Objects::nonNull)
                .map(FactionModel::getSource)
                .anyMatch(source::equals);
    }

    private static void promptAcculturation(Game game, String winner) {
        FactionModel faction = Mapper.getFaction(game.getStoredValue("theodisiAcculturationFaction"));
        if (faction == null) return;
        if ("for".equalsIgnoreCase(winner)) {
            String units = getNonStructureStartingUnits(faction.getStartingFleet());
            for (Player player : game.getRealPlayers()) {
                List<Button> buttons = game.getTileMap().values().stream()
                        .filter(tile -> FoWHelper.playerHasShipsInSystem(player, tile))
                        .map(tile -> Buttons.green(
                                player.factionButtonChecker() + ACCULTURATION_PLACE + faction.getAlias() + "|"
                                        + tile.getPosition(),
                                "Place units in " + tile.getRepresentationForButtons(game, player)))
                        .toList();
                if (units.isBlank() || buttons.isEmpty()) continue;
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + ", you may place the "
                                + faction.getFactionNameWithSourceEmoji()
                                + " starting ships and ground forces from your reinforcements in a system containing your ships due to _Acculturation_.",
                        buttons);
            }
            return;
        }
        for (Player player : game.getRealPlayers()) {
            List<String> startingTech = faction.getStartingTech();
            if (startingTech == null) continue;
            List<TechnologyModel> technologies = new ArrayList<>(startingTech.stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .filter(technology -> !player.hasTech(technology.getAlias()))
                    .toList());
            if (technologies.isEmpty()) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you may research each listed " + faction.getFactionName()
                            + " starting technology for free due to _Acculturation_.",
                    ListTechService.getTechButtons(technologies, player, "free"));
        }
    }

    private static String getNonStructureStartingUnits(String startingFleet) {
        if (startingFleet == null || startingFleet.isBlank()) return "";
        return java.util.Arrays.stream(startingFleet.split(","))
                .map(String::trim)
                .filter(unit -> !unit.matches(".*\\b(pds|space dock|spacedock|sd)\\b.*"))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @ButtonHandler(ACCULTURATION_PLACE)
    public static void placeAcculturationUnits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(ACCULTURATION_PLACE.length()).split("\\|", 2);
        if (values.length != 2) return;
        FactionModel faction = Mapper.getFaction(values[0]);
        Tile tile = game.getTileByPosition(values[1]);
        if (faction == null || tile == null || !FoWHelper.playerHasShipsInSystem(player, tile)) return;
        String units = getNonStructureStartingUnits(faction.getStartingFleet());
        if (units.isBlank()) return;
        if (tile.getPlanetUnitHolders().isEmpty()) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), getSpaceUnitList(units));
        } else {
            AddUnitService.addUnitsToDefaultLocations(event, tile, game, player.getColor(), units);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + faction.getFactionName()
                        + " starting ships and ground forces in " + tile.getRepresentationForButtons(game, player)
                        + " due to _Acculturation_.");
        ButtonHelper.deleteMessage(event);
    }

    private static String getSpaceUnitList(String units) {
        return Helper.getUnitList(units).entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> entry.getValue() + " " + entry.getKey().value)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static void resolvePirateUprising(Game game, ButtonInteractionEvent event, String winner) {
        Tile tile = game.getTileByPosition(winner);
        if (tile == null) return;
        for (Player player : game.getRealPlayers()) {
            DestroyUnitService.destroyAllPlayerUnitsInSystem(event, game, player, tile, false);
        }
        Player neutral = game.getNeutral();
        AddUnitService.addUnits(event, tile, game, neutral.getColor(), "1 ws, 6 ff");
        for (var planet : tile.getPlanetUnitHolders()) {
            AddUnitService.addUnits(event, tile, game, neutral.getColor(), "1 mech " + planet.getName());
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "_Pirate Uprising_ destroyed all non-neutral units in " + tile.getRepresentation()
                        + " and placed the neutral pirate forces.");
    }

    private static void promptPlayers(Game game, String message) {
        for (Player player : game.getRealPlayers()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationNoPing() + ", " + message);
        }
    }
}
