package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.special.SetupNeutralPlayer;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.ExploreModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetService;
import ti4.service.planet.FlipTileService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.transaction.SendDebtService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.settings.users.UserSettingsManager;

public class ButtonHelperAbilities {

    @ButtonHandler("dataRecovery_")
    public static void dataRecovery(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        SendDebtService.sendDebt(p2, player, 1);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                player.getRepresentation() + " placed 1 of " + p2.getRepresentation()
                        + " control tokens on their sheet via their **Data Recovery** ability.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("mirvedaFS_")
    public static void mirvedaFS(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), "ff");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " has placed 1 fighter in the active system using the ability of the Nexus.");
    }

    @ButtonHandler("blackOps_")
    public static void blackOps(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int amount = Integer.parseInt(buttonID.split("_")[2]);
        player.clearDebt(p2, amount);
        String msg = player.getRepresentation() + " spent " + amount + " of " + p2.getRepresentation()
                + " control tokens on their sheet via their **Black Ops** ability to ";
        if (amount == 2) {
            msg += "draw 1 secret objective.";
            game.drawSecretObjective(player.getUserID());
            if (player.hasAbility("plausible_deniability")) {
                game.drawSecretObjective(player.getUserID());
                msg += " Drew a second secret objective due to **Plausible Deniability**.";
            }
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        } else {
            msg += "gain 1 command token and steal 1 of their opponents unscored secret objectives.";
            String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                    + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
            if (!p2.getSecretsUnscored().isEmpty()) {
                int randInt = ThreadLocalRandom.current()
                        .nextInt(0, p2.getSecretsUnscored().size());
                List<Map.Entry<String, Integer>> entries =
                        new ArrayList<>(p2.getSecretsUnscored().entrySet());
                Map.Entry<String, Integer> randomEntry = entries.get(randInt);
                p2.removeSecret(randomEntry.getValue());
                player.setSecret(randomEntry.getKey());
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, p2, event);
                game.checkSOLimit(player);
            } else {
                msg += " Alas, their opponent did not have an unscored secret objectives.";
            }
        }
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);

        ButtonHelper.deleteMessage(event);
    }

    public static void autoneticMemoryStep1(Game game, Player player, int count) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep2_" + count, "Use Autonetic Memory"));
        buttons.add(Buttons.red("autoneticMemoryDecline_" + count, "Decline"));
        String msg = player.getRepresentationUnfogged()
                + ", you may draw 1 fewer action card and utilize your **Autonetic Memory** ability. Please use or decline to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("startFacsimile_")
    public static void startFacsimile(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        String msg = player.getRepresentation() + ", please choose the opponent ship you wish to build using "
                + MiscEmojis.influence + " influence.";
        if (player.getPromissoryNotes().containsKey("dspnmort")
                && !player.getPromissoryNotesOwned().contains("dspnmort")) {
            PromissoryNoteHelper.resolvePNPlay("dspnmort", player, game, event);
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if ((player.unitBelongsToPlayer(unitEntry.getKey()))) continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            int totalUnits = unitEntry.getValue();
            if (totalUnits > 0) {
                String buttonID2 = "facsimileStep2_" + unitName;
                String buttonText = unitKey.unitName();
                Button validTile2 = Buttons.red(buttonID2, buttonText, unitKey.unitEmoji());
                buttons.add(validTile2);
            }
        }
        if (event.getMessageChannel().getName().contains("cards-info")) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("facsimileStep2_")
    public static void resolveFacsimileStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        UnitType unitType = Units.findUnitType(AliasHandler.resolveUnit(unit));
        TI4Emoji unitEmoji = unitType.getUnitTypeEmoji();
        String msg = player.getFactionEmoji() + " chose to produce a " + unitEmoji + unit
                + " with **Facsimile** or _Secrets of the Weave_ and will now spend influence to build it.";
        event.getMessage().delete().queue();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        String message2 = player.getRepresentation() + ", please choose the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
    }

    @ButtonHandler("startRallyToTheCause")
    public static void startRallyToTheCause(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                "Please choose the system to produce up to 2 ships in.",
                getTilesToRallyToTheCause(game, player));
    }

    @ButtonHandler("startBestow")
    public static void startBestow(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("bestowPart2_" + player.getFaction(), "Gain 2 Commodities"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        for (Player p2 : player.getNeighbouringPlayers(true)) {
            MessageHelper.sendMessageToChannelWithButtons(
                    p2.getCorrectChannel(),
                    p2.getRepresentation() + " your neighbor " + player.getRepresentationNoPing()
                            + " has chosen to allow you to gain 2 commodities (they would gain 1). Use buttons to decide if you wish to accept this offer.",
                    buttons);
        }
    }

    @ButtonHandler("bestowPart2_")
    public static void bestowPart2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        p2.setCommodities(p2.getCommodities() + 1);
        player.setCommodities(player.getCommodities() + 2);
        ButtonHelperAgents.toldarAgentInitiation(game, player, 2);
        ButtonHelperAgents.toldarAgentInitiation(game, p2, 1);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentation() + " you gained 1 commodity from " + player.getRepresentationNoPing()
                        + " accepting your _Bestow_ Honor ability.");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " you gained 2 commodities from " + p2.getRepresentationNoPing()
                        + "'s _Bestow_ Honor ability.");
    }

    @ButtonHandler("deployFreesystemsMech_")
    public static void deployFreesystemsMech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        if (player.getTg() < 1) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "You don't have a trade good to pay for the mech.");
            return;
        }
        player.setTg(player.getTg() - 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " paid 1 trade good to DEPLOY a mech to a planet adjacent the system they are resolving **Rally to the Cause** in.");
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                "Use buttons to deploy a mech to a system adjacent to the Rally'd system.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("rallyToTheCauseStep2_")
    public static void rallyToTheCauseStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String type = "sling";
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce the first of potentially 2 ships. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        String message2 = player.getRepresentation() + " Use the buttons to produce the second of potentially 2 ships. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        if (player.ownsUnit("freesystems_mech")
                && !ButtonHelper.isLawInPlay(game, "articles_war")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
            buttons = new ArrayList<>();
            boolean adj = false;
            for (String pos2 : FoWHelper.getAdjacentTilesAndNotThisTile(game, pos, player, false)) {
                Tile tile = game.getTileByPosition(pos2);
                for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                    if (player.getPlanetsAllianceMode().contains(planet.getName())) {
                        adj = true;
                    }
                }
            }
            if (adj) {
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "deployFreesystemsMech_" + pos,
                        "Pay 1 Trade Good to Deploy a Mech"));
                message = player.getRepresentation()
                        + " you may pay 1 trade good to DEPLOY a mech to a planet adjacent to the system you're resolving **Rally to the Cause** in. ";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getTilesToRallyToTheCause(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)
                    || tile.isHomeSystem(game)
                    || ButtonHelper.isTileLegendary(tile)
                    || tile.isMecatol()
                    || tile.getPlanetUnitHolders().isEmpty()) {
                continue;
            }
            buttons.add(Buttons.green(
                    "rallyToTheCauseStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    @ButtonHandler("mercenariesStep1_")
    public static void mercenariesStep1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        List<Button> buttons = new ArrayList<>();
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile tile2 = game.getTileByPosition(pos);
            UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                buttons.add(Buttons.green(
                        "mercenariesStep2_" + pos2 + "_" + pos, tile2.getRepresentationForButtons(game, player)));
            }
        }
        String msg = player.getRepresentation() + ", please choose the system you wish to pull fighters from.";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep2_")
    public static void mercenariesStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mercenariesStep3_" + pos2 + "_" + pos + "_1", "1 fighter"));
        if (tile2.getUnitHolders().get("space").getUnitCount(UnitType.Fighter, player.getColor()) > 1) {
            buttons.add(Buttons.green("mercenariesStep3_" + pos2 + "_" + pos + "_2", "2 fighters"));
        }
        String msg = player.getRepresentation() + ", please choose whether to pull 1 or 2 fighters.";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep3_")
    public static void mercenariesStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        String fighters = buttonID.split("_")[3];
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                String id = "mercenariesStep4_" + pos2 + "_" + pos + "_" + fighters + "_" + p2.getFaction();
                if (game.isFowMode()) {
                    buttons.add(Buttons.green(id, capitalize(p2.getColor())));
                } else {
                    buttons.add(Buttons.green(id, p2.getFactionModel().getFactionName()));
                }
            }
        }
        String msg = player.getRepresentation() + ", please choose which player to give the fighters too.";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("mercenariesStep4_")
    public static void mercenariesStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos2 = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos2);
        String pos = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos);
        String fighters = buttonID.split("_")[3];
        String faction = buttonID.split("_")[4];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        RemoveUnitService.removeUnits(event, tile2, game, player.getColor(), fighters + " fighters");
        AddUnitService.addUnits(event, tile, game, p2.getColor(), fighters + " fighters");
        String msg = player.getRepresentation() + " used their **Mercenaries** ability and transferred " + fighters
                + " fighter" + ("1".equals(fighters) ? "" : "s") + " from "
                + tile2.getRepresentationForButtons(game, player) + " to "
                + tile.getRepresentationForButtons(game, player) + " and gave them to "
                + p2.getFactionEmojiOrColor() + ".";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("bindingDebtsRes_")
    public static void bindingDebtRes(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        Player vaden = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        vaden.setTg(vaden.getTg() + 1);
        pillageCheck(vaden, game);
        player.setTg(player.getTg() - 1);
        int amount = Math.min(2, vaden.getDebtTokenCount(player.getColor()));
        vaden.clearDebt(player, amount);
        String msg = player.getFactionEmojiOrColor() + " paid 1 trade good to "
                + vaden.getFactionEmojiOrColor()
                + "to clear 2 debt tokens via the **Binding Debts** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    @ButtonHandler("autoneticMemoryDecline_")
    public static void autoneticMemoryDecline(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        game.drawActionCard(player.getUserID(), count);
        ActionCardHelper.sendActionCardInfo(game, player, event);
        ButtonHelper.checkACLimit(game, player);
    }

    private static List<String> getTrapNames() {
        List<String> trapNames = new ArrayList<>();
        trapNames.add("Minefields");
        trapNames.add("Interference Grid");
        trapNames.add("Feint");
        trapNames.add("Gravitic Inhibitor");
        trapNames.add("Account Siphon");
        // trapNames.add("Saboteurs");
        return trapNames;
    }

    @ButtonHandler("resolveGrace_")
    public static void resolveGrace(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String msg = player.getFactionEmoji() + " is resolving the **Grace** ability.";
        int scPlayed = Integer.parseInt(buttonID.split("_")[1]);
        if (!player.hasAbility("grace")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "To " + player.getFactionEmoji() + ": This button ain't for you.");
            return;
        }
        player.addExhaustedAbility("grace");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " use buttons to resolve **Grace**.\n"
                        + "-# Reminder that you have to spend a command token from your strategy pool if applicable, and that you may only do one of these.",
                getGraceButtons(game, player, scPlayed));
    }

    public static List<Button> getGraceButtons(Game game, Player edyn, int scPlayed) {
        List<Button> scButtons = new ArrayList<>();
        scButtons.add(Buttons.gray("spendAStratCC", "Spend a Strategy Token"));
        if (scPlayed > 1
                && (game.getScPlayed().get(1) == null || !game.getScPlayed().get(1))) {
            scButtons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens"));
            // scButtons.add(Buttons.red("leadershipExhaust", "Exhaust Planets"));
        }
        if (scPlayed > 2
                && (game.getScPlayed().get(2) == null || !game.getScPlayed().get(2))) {
            scButtons.add(Buttons.green("diploRefresh2", "Ready 2 Planets"));
        }
        if (scPlayed > 3
                && (game.getScPlayed().get(3) == null || !game.getScPlayed().get(3))) {
            scButtons.add(Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (scPlayed > 4
                && (game.getScPlayed().get(4) == null || !game.getScPlayed().get(4))) {
            scButtons.add(Buttons.green("construction_spacedock", "Place 1 Space Dock", UnitEmojis.spacedock));
            scButtons.add(Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds));
        }
        if (scPlayed > 5
                && (game.getScPlayed().get(5) == null || !game.getScPlayed().get(5))) {
            scButtons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (scPlayed > 6
                && (game.getScPlayed().get(6) == null || !game.getScPlayed().get(6))) {
            scButtons.add(Buttons.green("warfareBuild", "Build At Home"));
        }
        if (scPlayed > 7
                && (game.getScPlayed().get(7) == null || !game.getScPlayed().get(7))) {
            scButtons.add(Buttons.GET_A_TECH);
        }
        if (scPlayed > 8
                && (game.getScPlayed().get(8) == null || !game.getScPlayed().get(8))) {
            scButtons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        scButtons.add(Buttons.red("deleteButtons", "Done Resolving"));

        return scButtons;
    }

    @ButtonHandler("setTrapStep1")
    public static void setTrapStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("setTrapStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("setTrapStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", please choose whose planet you wish to put a trap on.",
                buttons);
    }

    @ButtonHandler("setTrapStep2_")
    public static void setTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Buttons.gray("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to put a trap on.",
                buttons);
    }

    @ButtonHandler("setTrapStep3_")
    public static void setTrapStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : getUnusedTraps(game, player)) {
            availableTraps.add(Buttons.green("setTrapStep4_" + planet + "_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the trap you wish to set on the planet "
                + Helper.getPlanetRepresentation(planet, game) + ".";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("removeTrapStep1")
    public static void removeTrapStep1(Game game, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Buttons.green("removeTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the trap you wish to remove.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep1")
    public static void revealTrapStep1(Game game, Player player) {
        List<Button> availableTraps = new ArrayList<>();
        for (String availableTrap : player.getTrapCardsPlanets().keySet()) {
            availableTrap = translateNameIntoTrapIDOrReverse(availableTrap);
            availableTraps.add(Buttons.green("revealTrapStep2_" + availableTrap, availableTrap));
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the trap you wish to reveal.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, availableTraps);
    }

    @ButtonHandler("revealTrapStep2_")
    public static void revealTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planet, trap, player, true);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("removeTrapStep2_")
    public static void removeTrapStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trap = buttonID.split("_")[1];
        trap = translateNameIntoTrapIDOrReverse(trap);
        String planet = player.getTrapCardsPlanets().get(trap);
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planet, trap, player, false);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("setTrapStep4_")
    public static void setTrapStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String trap = translateNameIntoTrapIDOrReverse(buttonID.split("_")[2]);
        event.getMessage().delete().queue();
        DiscordantStarsHelper.setTrapForPlanet(event, game, planet, trap, player);
    }

    public static List<String> getPlanetsWithTraps(Game game) {
        List<String> trappedPlanets = new ArrayList<>();
        for (String trapName : getTrapNames()) {
            if (!game.getStoredValue(trapName).isEmpty()) {
                trappedPlanets.add(game.getStoredValue(trapName));
            }
        }
        return trappedPlanets;
    }

    private static List<String> getUnusedTraps(Game game, Player player) {
        List<String> unusedTraps = new ArrayList<>();
        for (String trapName : getTrapNames()) {
            if (!player.getTrapCardsPlanets().containsKey(translateNameIntoTrapIDOrReverse(trapName))) {
                unusedTraps.add(trapName);
            }
        }
        return unusedTraps;
    }

    public static String translateNameIntoTrapIDOrReverse(String nameOrTrapID) {
        switch (nameOrTrapID) {
            case "Minefields" -> {
                return "dshc_3_lizho";
            }
            case "Interference Grid" -> {
                return "dshc_1_lizho";
            }
            case "Feint" -> {
                return "dshc_6_lizho";
            }
            case "Gravitic Inhibitor" -> {
                return "dshc_4_lizho";
            }
            case "Account Siphon" -> {
                return "dshc_2_lizho";
            }
            case "Saboteurs" -> {
                return "dshc_5_lizho";
            }
            case "dshc_3_lizho" -> {
                return "Minefields";
            }
            case "dshc_1_lizho" -> {
                return "Interference Grid";
            }
            case "dshc_6_lizho" -> {
                return "Feint";
            }
            case "dshc_4_lizho" -> {
                return "Gravitic Inhibitor";
            }
            case "dshc_2_lizho" -> {
                return "Account Siphon";
            }
            case "dshc_5_lizho" -> {
                return "Saboteurs";
            }
        }
        return "nope";
    }

    public static void addATrapToken(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        boolean addedYet = false;
        for (int x = 1; x < 7 && !addedYet; x++) {
            String tokenName = "attachment_lizhotrap" + x + ".png";
            if (!uH.getTokenList().contains(tokenName)) {
                addedYet = true;
                tile.addToken(tokenName, uH.getName());
            }
        }
    }

    public static void removeATrapToken(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return;
        }
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        boolean removedYet = false;
        for (int x = 1; x < 7 && !removedYet; x++) {
            String tokenName = "attachment_lizhotrap" + x + ".png";
            if (uH.getTokenList().contains(tokenName)) {
                removedYet = true;
                tile.removeToken(tokenName, uH.getName());
            }
        }
        if (!removedYet) {
            String tokenName = "attachment_lizhotrap.png";
            if (uH.getTokenList().contains(tokenName)) {
                tile.removeToken(tokenName, uH.getName());
            }
        }
    }

    @ButtonHandler("autoneticMemoryStep2_")
    public static void autoneticMemoryStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        event.getMessage().delete().queue();
        int count = Integer.parseInt(buttonID.split("_")[1]);
        game.drawActionCard(player.getUserID(), count - 1);
        ActionCardHelper.sendActionCardInfo(game, player, event);
        String msg2 = player.getRepresentationNoPing() + " is choosing to resolve their **Autonetic Memory** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
                + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void autoneticMemoryStep3b(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "gf", "placeOneNDone_skipbuild");
        String message = player.getRepresentationUnfogged() + " Use buttons to drop 1 infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void autoneticMemoryStep3a(Game game, Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
        ActionCardHelper.pickACardFromDiscardStep1(game, player);
    }

    public static void addOmenDie(Game game, int omenDie) {
        String omenDice;
        if (!game.getStoredValue("OmenDice").isEmpty()) {
            omenDice = game.getStoredValue("OmenDice");
            omenDice += "_" + omenDie;
            game.setStoredValue("OmenDice", omenDice);
        } else {
            game.setStoredValue("OmenDice", "" + omenDie);
        }
    }

    private static void removeOmenDie(Game game, int omenDie) {
        String omenDice;
        if (!game.getStoredValue("OmenDice").isEmpty()) {
            omenDice = game.getStoredValue("OmenDice");
            omenDice = omenDice.replaceFirst("" + omenDie, "");
            omenDice = omenDice.replace("__", "_");
            game.setStoredValue("OmenDice", omenDice);
        }
    }

    public static List<Integer> getAllOmenDie(Game game) {
        List<Integer> dice = new ArrayList<>();
        for (String dieResult : game.getStoredValue("OmenDice").split("_")) {
            if (!dieResult.isEmpty() && !dieResult.contains("_")) {
                int die = Integer.parseInt(dieResult);
                dice.add(die);
            }
        }
        return dice;
    }

    @ButtonHandler("getOmenDice")
    public static void offerOmenDiceButtons(Game game, Player player) {
        offerOmenDiceButtons2(game, player, "no");
    }

    @ButtonHandler("getOmenDice2")
    public static void offerOmenDiceButtons2(Game game, Player player, String agent) {
        String msg = player.getRepresentationUnfogged()
                + " you may play an Omen die with the following buttons. Duplicate dice are not shown.";
        List<Button> buttons = new ArrayList<>();
        List<Integer> dice = new ArrayList<>();
        for (int die : getAllOmenDie(game)) {
            if (!dice.contains(die)) {
                buttons.add(Buttons.green("useOmenDie_" + die + "_" + agent, "Use Result: " + die));
                dice.add(die);
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("useOmenDie_")
    public static void useOmenDie(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        int die = Integer.parseInt(buttonID.split("_")[1]);
        if ("no".equalsIgnoreCase(buttonID.split("_")[2])) {
            removeOmenDie(game, die);
        }
        String msg = player.getRepresentationUnfogged() + " used an **Omen** die with the number " + die + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    private static void rollOmenDiceAtStartOfStrat(Game game, Player myko) {
        game.setStoredValue("OmenDice", "");
        StringBuilder msg = new StringBuilder(
                myko.getRepresentationUnfogged() + " rolled 4 **Omen** dice and rolled the following numbers:");
        for (int x = 0; x < 4; x++) {
            Die d1 = new Die(6);
            msg.append(" ").append(d1.getResult());
            addOmenDie(game, d1.getResult());
        }
        MessageHelper.sendMessageToChannel(
                myko.getCorrectChannel(), msg.append(".").toString());
    }

    @ButtonHandler("pillage_")
    public static void pillage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = game.getPlayerFromColorOrFaction(colorPlayer);
        if (pillaged == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        if (checkedStatus.contains("unchecked")) {
            List<Button> buttons = new ArrayList<>();
            String message2 =
                    "Please confirm this is a valid **Pillage** opportunity and that you wish to **Pillage**.";
            buttons.add(Buttons.red(
                    player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checked",
                    "Pillage 1 Trade Good"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Buttons.red(
                        player.getFinsFactionCheckerPrefix() + "pillage_" + pillaged.getColor() + "_checkedcomm",
                        "Pillage 1 Commodity"));
            }
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {

            player.setPillageCounter(player.getPillageCounter() + 1);
            pillaged.setPillageCounter(pillaged.getPillageCounter() + 1);
            String pillagerMessage = player.getRepresentationUnfogged()
                    + " you succesfully **Pillage**'d, so your ~~doubloons~~ trade goods have gone from "
                    + player.getTg() + " to "
                    + (player.getTg() + 1) + ". The number of innocent merchant ships you have looted this game is "
                    + player.getPillageCounter();
            if (player.getPillageCounter() < 5) {
                pillagerMessage += ", which is not enough to be well-known yet, keep trying young matey.";
            } else if (player.getPillageCounter() < 10) {
                pillagerMessage +=
                        ", which is a small but significant amount, you'll be an infamous pirate yet, just keep at it for 10, maybe 20, more rounds.";
            } else if (player.getPillageCounter() < 15) {
                pillagerMessage +=
                        ", which is quite the treasure hoard of ill-gotten goods. Hope you didn't spend it all in one place.";
            } else if (player.getPillageCounter() < 20) {
                pillagerMessage +=
                        ", which is starting to be a bit legendary. Sort of surprised the table hasn't united against you yet";
            } else {
                pillagerMessage +=
                        ", which is enough to ensure your name goes down in async history as one of the most successful pirates ever to sail the intergalactic seas.";
            }
            String pillagedMessage = "Arrr, " + pillaged.getRepresentationUnfogged()
                    + ", it do seem ye have been **Pillage**'d 🏴‍☠️🏴‍☠️🏴‍☠️";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage += ", so your worthless commodities went from " + pillaged.getCommodities() + " to "
                        + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage +=
                        ", so your trade goods went from " + pillaged.getTg() + " to " + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }

            var userSettings = UserSettingsManager.get(player.getUserID());
            if (!userSettings.isPrefersPillageMsg()) {
                pillagerMessage = player.getRepresentationUnfogged()
                        + " you succesfully **Pillage**'d, so your ~~doubloons~~ trade goods have gone from "
                        + player.getTg() + " to "
                        + (player.getTg() + 1) + ".";
            } else {
                pillagedMessage +=
                        " This number of times your gold has been forcefully liberated from your grasping hands for the benefit of the needy this game is "
                                + pillaged.getPillageCounter() + ".";
            }
            player.setTg(player.getTg() + 1);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pillagerMessage);
                MessageHelper.sendMessageToChannel(pillaged.getCorrectChannel(), pillagedMessage);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), pillagerMessage + "\n" + pillagedMessage);
            }
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(
                        "FFCC_" + player.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(),
                        "Use Mentak Agent",
                        FactionEmojis.Mentak));
                buttons.add(Buttons.red("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "Wanna use " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Suffi An, the Mentak"
                                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " Agent?",
                        buttons);
            }
            for (Player p2 : game.getRealPlayers()) {
                if (p2 != pillaged
                        && p2 != player
                        && p2.hasUnexhaustedLeader("yssarilagent")
                        && player.hasLeader("mentakagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            "FFCC_" + p2.getFaction() + "_" + "exhaustAgent_mentakagent_" + pillaged.getFaction(),
                            "Use Mentak Agent",
                            FactionEmojis.Mentak));
                    buttons.add(Buttons.red("deleteButtons", "Done"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            p2.getCorrectChannel(),
                            p2.getRepresentation() + "Wanna use "
                                    + (p2.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                    + "Suffi An, the Mentak"
                                    + (p2.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent?",
                            buttons);
                }
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getMitosisOptions(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("mitosisInf", "Place 1 infantry"));
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") < 4) {
            buttons.add(Buttons.blue("mitosisMech", "Remove 1 Infantry to Deploy 1 Mech"));
        }
        return buttons;
    }

    @ButtonHandler("getDiplomatsButtons")
    public static void resolveGetDiplomatButtons(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String message = player.getRepresentation() + ", please choose the planet you wish to exhaust.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, getDiplomatButtons(game, player));
    }

    public static List<Button> getDiplomatButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().contains("token_freepeople.png")) {
                    buttons.add(Buttons.green(
                            "exhaustViaDiplomats_" + unitHolder.getName(),
                            Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                }
            }
        }
        return buttons;
    }

    public static void resolveFreePeopleAbility(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet) {
                    String planet = unitHolder.getName();
                    boolean alreadyOwned = false;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_.getPlanets().contains(planet)) {
                            alreadyOwned = true;
                            break;
                        }
                    }
                    if (!alreadyOwned && !Constants.MECATOLS.contains(planet)) {
                        unitHolder.addToken("token_freepeople.png");
                    }
                }
            }
        }
    }

    @ButtonHandler("exhaustViaDiplomats_")
    public static void resolveDiplomatExhaust(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String message = player.getFactionEmoji() + " exhausted the unowned planet "
                + Helper.getPlanetRepresentation(planet, game) + " using their **Diplomats** ability.";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        unitHolder.removeToken("token_freepeople.png");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("addTombToken_")
    public static void addTombToken(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String message = player.getFactionEmoji() + " added a Tomb token to "
                + Helper.getPlanetRepresentation(planet, game) + ".";
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        if (unitHolder.getTokenList().contains("token_tomb.png")) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getFactionEmoji() + " had already added a Tomb token to "
                            + Helper.getPlanetRepresentation(planet, game) + ".");

            return;
        }
        unitHolder.addToken("token_tomb.png");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
    }

    @ButtonHandler("startAncientEmpire")
    public static void startAncientEmpire(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String message = player.getRepresentation() + ", please choose a planet to add a Tomb token to.";
        List<Button> buttons = new ArrayList<>();
        for (String planet : game.getPlanets()) {
            UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (unitHolder != null) {
                if (unitHolder.getTokenList().contains("token_tomb.png")) {
                    continue;
                }
                buttons.add(Buttons.green("addTombToken_" + planet, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    @ButtonHandler("mitosisInf")
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "infantry", "placeOneNDone_skipbuild"));
        String message = player.getRepresentationUnfogged() + ", please choose which planet to place an infantry on.";

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("mitosisMech")
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, game));
        String message = player.getRepresentationUnfogged()
                + ", please choose where you wish to replace an infantry with a mech.";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmojiOrColor() + " is resolving **Mitosis**.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game game) {
        return getPlanetPlaceUnitButtonsForMechMitosis(player, game, "mitosis");
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game game, String reason) {
        List<Button> planetButtons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);

                if (numInf > 0) {
                    String buttonID = player.getFinsFactionCheckerPrefix() + "mitoMechPlacement_" + tile.getPosition()
                            + "_" + unitHolder.getName() + "_" + reason;
                    if ("space".equalsIgnoreCase(unitHolder.getName())) {
                        planetButtons.add(Buttons.green(
                                buttonID, "Space Area of " + tile.getRepresentationForButtons(game, player)));
                    } else {
                        planetButtons.add(
                                Buttons.green(buttonID, Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                    }
                }
            }
        }
        return planetButtons;
    }

    @ButtonHandler("putSleeperOnPlanet_")
    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message =
                player.getFactionEmojiOrColor() + " put a Sleeper on " + Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("orbitalMechDrop_")
    public static void orbitalMechDrop(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 mech " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(true, false) + " dropped a mech on "
                        + Helper.getPlanetRepresentation(planet, game) + " for the cost of " + MiscEmojis.Resources_3);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("finishComponentAction_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getMantleCrackingButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        int coreCount = 0;
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia") || planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
            if (planet == null) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            if (!planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)
                    && !Constants.MECATOLS.contains(planetName)
                    && !tile.isHomeSystem(game)) {
                buttons.add(
                        Buttons.gray("mantleCrack_" + planetName, Helper.getPlanetRepresentation(planetName, game)));
            } else {
                if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                    coreCount += 1;
                }
            }
        }
        if (coreCount > 2) {
            return new ArrayList<>();
        }
        return buttons;
    }

    public static List<Button> getSuperWeaponButtonsPart1(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia") || planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
            if (planet == null) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            boolean hasSuperweapon = false;
            for (String token : planet.getTokenList()) {
                if (token.contains("superweapon")) {
                    hasSuperweapon = true;
                    break;
                }
            }
            if (!hasSuperweapon) {
                buttons.add(Buttons.gray(
                        player.finChecker() + "superWeaponPart2_" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        return buttons;
    }

    private static Tile getLocationOfSuperweapon(Game game, String name) {
        Tile tile = null;
        for (Tile loc : game.getTileMap().values()) {
            for (UnitHolder uH : loc.getPlanetUnitHolders()) {
                for (String token : uH.getTokenList()) {
                    if (token.contains("superweapon_" + name)) {
                        return loc;
                    }
                }
            }
        }
        return tile;
    }

    @ButtonHandler("exhaustSuperweapon_")
    public static void exhaustSuperweapon(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String name = buttonID.split("_")[1];
        String superweapon = "superweapon" + buttonID.split("_")[1];
        player.addExhaustedRelic(superweapon);
        Tile tile = getLocationOfSuperweapon(game, name);
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = new ArrayList<>();
        switch (name) {
            case "grom" -> {
                for (String adj : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, true)) {
                    buttons.add(Buttons.gray(
                            "gromPart2_" + adj, game.getTileByPosition(adj).getRepresentationForButtons()));
                }
            }
            case "mors" -> {
                Set<String> adjPos = FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, true);
                for (Tile loc : game.getTileMap().values()) {
                    if (!adjPos.contains(loc.getPosition()))
                        buttons.add(Buttons.gray("morsPart2_" + loc.getPosition(), loc.getRepresentationForButtons()));
                }
            }
            case "glatison" -> {
                for (Tile loc : game.getTileMap().values()) {
                    loc.removeAllUnitDamage(player.getColor());
                }
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji()
                                + " repaired all their damaged units everywhere by exhausting the _Glatison_ Superweapon ability."
                                + (RandomHelper.isOneInX(20)
                                        ? " It kinda stretches the definition of \"Super__weapon__\", hey."
                                        : ""));
                return;
            }
            case "caled" -> {
                ButtonHelperModifyUnits.resolveAssaultCannonNDihmohnCommander(
                        "id_caled_" + buttonID.split("_")[2], event, player, game);
                return;
            }
            case "availyn" -> {
                availynStep1(game, player, event, "availyn_" + buttonID.split("_")[2]);
                return;
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please the system you wish to target.",
                buttons);
    }

    @ButtonHandler("morsPart2_")
    public static void morsPart2(Player belk, Game game, String buttonID, ButtonInteractionEvent event) {
        String location = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(location);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            for (Player player : game.getRealPlayers()) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    if (unitModel == null) continue;
                    UnitKey unitKey = unitEntry.getKey();
                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null
                            && unitHolder.getUnitDamage().get(unitKey) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                    }
                    int totalUnits = unitEntry.getValue() - damagedUnits;
                    if (totalUnits > 0 && unitModel.getSustainDamage()) {
                        tile.addUnitDamage(unitHolder.getName(), unitKey, totalUnits);
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                belk.getCorrectChannel(),
                belk.getFactionEmoji() + " damaged all units in the system " + tile.getRepresentation()
                        + " by exhausting the _Mors_ Superweapon ability.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gromPart2_")
    public static void gromPart2(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String location = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(location);
        List<Button> buttons = new ArrayList<>();

        buttons.add(Buttons.red("getDamageButtons_" + location + "_spacecombat", "Assign Hits"));
        for (Player victim : game.getRealPlayers()) {
            UnitHolder uH = tile.getSpaceUnitHolder();
            if (uH.getUnitCount(UnitType.Fighter, victim) > 0) {
                String result = player.getFactionEmojiOrColor()
                        + " rolling for the _Grom_ Superweapon against fighters owned by " + victim.getRepresentation()
                        + ":\n";
                int totalHits = 0;
                StringBuilder resultBuilder = new StringBuilder(result);
                int toHit = 4;
                int modifierToHit = 0;
                int extraRollsForUnit = 0;
                int numRollsPerUnit = 1;
                int numRolls = uH.getUnitCount(UnitType.Fighter, victim);
                List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                player.setExpectedHitsTimes10(
                        player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
                int hitRolls = DiceHelper.countSuccesses(resultRolls);
                totalHits += hitRolls;
                String unitRoll = CombatMessageHelper.displayUnitRoll(
                        player.getUnitByID("belkosea_flagship"),
                        toHit,
                        modifierToHit,
                        1,
                        numRollsPerUnit,
                        extraRollsForUnit,
                        resultRolls,
                        hitRolls);
                resultBuilder.append(unitRoll);

                result = resultBuilder.toString();
                result += CombatMessageHelper.displayHitResults(totalHits);
                player.setActualHits(player.getActualHits() + totalHits);

                if (totalHits > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            victim.getCorrectChannel(),
                            result + "\n" + victim.getRepresentation()
                                    + ", please assign any hits using this \"Assign Hits\" button.",
                            buttons);
                } else {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            result + "\n" + victim.getRepresentation() + ", none of your fighters were hit.");
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " fired ANTI-FIGHTER BARRAGE 4 against each fighter in the system "
                        + tile.getRepresentation() + " by exhausting the _Grom_ superweapon ability.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("superWeaponPart2_")
    public static void superWeaponButtonsPart2(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String extra;

        if (player.hasRelic("superweaponavailyn")) {
            extra = " (In Use)";
        } else {
            extra = "";
        }
        buttons.add(Buttons.gray(player.finChecker() + "superWeaponPart3_availyn_" + planetName, "Availyn" + extra));

        if (player.hasRelic("superweaponcaled")) {
            extra = " (In Use)";
        } else {
            extra = "";
        }
        buttons.add(Buttons.gray(player.finChecker() + "superWeaponPart3_caled_" + planetName, "Caled" + extra));

        if (player.hasRelic("superweaponglatison")) {
            extra = " (In Use)";
        } else {
            extra = "";
        }
        buttons.add(Buttons.gray(player.finChecker() + "superWeaponPart3_glatison_" + planetName, "Glatison" + extra));

        if (player.hasRelic("superweapongrom")) {
            extra = " (In Use)";
        } else {
            extra = "";
        }
        buttons.add(Buttons.gray(player.finChecker() + "superWeaponPart3_grom_" + planetName, "Grom" + extra));

        if (player.hasRelic("superweaponmors")) {
            extra = " (In Use)";
        } else {
            extra = "";
        }
        buttons.add(Buttons.gray(player.finChecker() + "superWeaponPart3_mors_" + planetName, "Mors" + extra));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please pick which Superweapon you wish to place on your planet."
                        + "\n-# Note that you can only have one of each Superweapon, but may remove Superweapons from unlocked systems if you wish to rebuild them somewhere else.",
                buttons);
    }

    @ButtonHandler("superWeaponPart3_")
    public static void superWeaponButtonsPart3(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[2];
        String superweaponName = buttonID.split("_")[1];

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " put the _" + capitalize(superweaponName) + "_ Superweapon on the planet "
                        + Helper.getPlanetName(planetName) + " for a cost of 5 resources or influence."
                        + Mapper.getRelic("superweapon" + superweaponName).getSimpleRepresentation());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "both");
        Button DoneExhausting = Buttons.red("finishComponentAction_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        player.addRelic("superweapon" + superweaponName);
        CommanderUnlockCheckService.checkPlayer(player, "belkosea");
        Tile tile = game.getTileFromPlanet(planetName);
        tile.addToken("attachment_superweapon_" + superweaponName + ".png", planetName);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please pay 5 influence or resources for the Superweapon.",
                buttons);
    }

    @ButtonHandler("resolveShipOrder_")
    public static void resolveAxisOrderExhaust(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String order = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " is using _"
                        + Mapper.getRelic(order).getName() + "_.");
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(
                    player, game, "dreadnought", "placeOneNDone_skipbuild", event));
            message = "Use buttons to place 1 dreadnought in a system your command token and your other ships.";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(
                    player, game, "carrier", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system your command token and your other ships.";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(
                    player, game, "cruiser", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system your command token and your other ships.";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(
                    player, game, "2destroyer", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system your command token and your other ships.";
            techName = "dd2";
        }
        message = player.getRepresentationUnfogged() + " " + message;
        ButtonHelper.deleteTheOneButton(event);
        player.addExhaustedRelic(order);
        for (Player p2 : game.getRealPlayers()) {
            if (game.playerHasLeaderUnlockedOrAlliance(p2, "axiscommander") && !p2.hasTech(techName)) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Buttons.GET_A_TECH);
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged()
                                + ", a player has resolved the "
                                + Mapper.getRelic(order).getName()
                                + " Axis Order, and you may use the button to gain the corresponding unit upgrade technology if you pay 6 resources.",
                        buttons2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static int getNumberOfDifferentAxisOrdersBought(Player player, Game game) {
        int num = 0;
        String relicName = "axisorderdd";
        String extra = "duplicate";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
                || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisordercr";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
                || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisordercv";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
                || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        relicName = "axisorderdn";
        if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName)
                || ButtonHelperFactionSpecific.somebodyHasThisRelic(game, relicName + extra)) {
            num++;
        }
        return num;
    }

    public static List<Button> getBuyableAxisOrders(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        int maxCost = player.getCommodities();

        Map<String, Integer> orderDeck = Map.of(
                "axisorderdd", 1,
                "axisorderddduplicate", 1,
                "axisordercr", 1,
                "axisordercrduplicate", 1,
                "axisordercv", 2,
                "axisordercvduplicate", 2,
                "axisorderdn", 3,
                "axisorderdnduplicate", 3);
        for (Map.Entry<String, Integer> order : orderDeck.entrySet()) {
            String orderName = order.getKey();
            int orderCost = order.getValue();
            if (orderCost <= maxCost) {
                if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(game, orderName)) {
                    buttons.add(Buttons.gray(
                            "buyAxisOrder_" + orderName + "_" + orderCost,
                            "Buy an " + Mapper.getRelic(orderName).getName() + " for " + orderCost + " commodit"
                                    + (orderCost == 1 ? "y" : "ies")));
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    private static List<Button> getReturnableAxisOrders(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();

        Map<String, Integer> orderDeck = Map.of(
                "axisorderdd", 1,
                "axisorderddduplicate", 1,
                "axisordercr", 1,
                "axisordercrduplicate", 1,
                "axisordercv", 2,
                "axisordercvduplicate", 2,
                "axisorderdn", 3,
                "axisorderdnduplicate", 3);
        for (Map.Entry<String, Integer> order : orderDeck.entrySet()) {
            String orderName = order.getKey();

            if (ButtonHelperFactionSpecific.somebodyHasThisRelic(game, orderName)) {
                Player p3 = player;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2.getRelics().contains(orderName)) {
                        p3 = p2;
                    }
                }
                buttons.add(Buttons.gray(
                        "returnAxisOrder_" + orderName + "_" + p3.getFaction(),
                        "Return " + Mapper.getRelic(orderName).getName() + " held by " + p3.getColor()));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    @ButtonHandler("getAxisOrderReturns")
    public static void getAxisOrderReturns(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                "You can use these buttons to return an Axis Order to the deck, normally done because you bought one by mistake.",
                getReturnableAxisOrders(player, game));
    }

    @ButtonHandler("buyAxisOrder_")
    public static void resolveAxisOrderBuy(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String relicName = buttonID.split("_")[1];
        String cost = buttonID.split("_")[2];
        int lostComms = Integer.parseInt(cost);
        int oldComms = player.getCommodities();
        if (lostComms > oldComms) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you don't have " + lostComms + " commodit"
                            + (lostComms == 1 ? "y" : "ies") + " (you only have " + oldComms + " commodit"
                            + (oldComms == 1 ? "y" : "ies") + ".");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " acquired "
                        + Mapper.getRelic(relicName).getName()
                        + " and paid " + lostComms + " commodit" + (lostComms == 1 ? "y" : "ies") + " (" + oldComms
                        + "->" + player.getCommodities()
                        + ").");
        CommanderUnlockCheckService.checkPlayer(player, "axis");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("returnAxisOrder_")
    public static void returnAxisOrder(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String relicName = buttonID.split("_")[1];
        String owner = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(owner);
        p2.removeRelic(relicName);
        if (p2 != player) {
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your "
                            + Mapper.getRelic(relicName).getName()
                            + " Axis Order was returned to the pile (probably to fix some mistake).");
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " returned the "
                        + Mapper.getRelic(relicName).getName() + " Axis Order (probably to fix some mistake).");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> offerOlradinConnectButtons(Player player, Game game, String planetName) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.equalsIgnoreCase(planetName)) {
                continue;
            }
            buttons.add(Buttons.green(
                    "olradinConnectStep2_" + planetName + "_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler("olradinConnectStep2_")
    public static void resolveOlradinConnectStep2(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet1 = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        player.setHasUsedPeopleConnectAbility(true);

        Tile source = game.getTileFromPlanet(planet1);
        Tile dest = game.getTileFromPlanet(planet2);
        MoveUnitService.moveUnits(event, source, game, player.getColor(), "inf " + planet1, dest, planet2);

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " moved 1 infantry from "
                        + Helper.getPlanetRepresentation(planet1, game) + " to "
                        + Helper.getPlanetRepresentation(planet2, game) + " using _Policy - The People: Connect ➕_.");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("olradinPreserveStep2_")
    public static void resolveOlradinPreserveStep2(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String type = buttonID.split("_")[1];
        player.setHasUsedEnvironmentPreserveAbility(true);
        StringBuilder sb = new StringBuilder();
        String cardID = game.drawExplore(type);
        ExploreModel card = Mapper.getExplore(cardID);
        sb.append(card.textRepresentation()).append(System.lineSeparator());
        String cardType = card.getResolution();
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            sb.append(player.getRepresentationUnfogged()).append(" Gained relic fragment\n");
            player.addFragment(cardID);
            game.purgeExplore(cardID);
        } else {
            if (buttonID.contains("prof")) {
                sb.append(player.getRepresentationUnfogged()).append(" Gained 1 commodity\n");
                player.setCommodities(player.getCommodities() + 1);
            }
        }
        if (!buttonID.contains("prof")) {
            event.getMessage().delete().queue();
        } else {
            ButtonHelper.deleteTheOneButton(event);
        }

        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
    }

    public static List<Button> getOlradinPreserveButtons(Game game, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypeOfPlanet(game, planet);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Buttons.green("olradinPreserveStep2_industrial", "Explore Industrial"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Buttons.blue("olradinPreserveStep2_cultural", "Explore Cultural"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Buttons.red("olradinPreserveStep2_hazardous", "Explore Hazardous"));
            }
        }
        return buttons;
    }

    public static void offerOrladinPlunderButtons(Player player, Game game, String planet) {
        if (!player.isHasUsedEnvironmentPlunderAbility()
                && player.hasAbility("policy_the_environment_plunder")
                && ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")) {
            UnitHolder planetUnit = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Planet planetReal = (Planet) planetUnit;
            List<Button> buttons = new ArrayList<>();
            if (planetReal != null
                    && isNotBlank(planetReal.getOriginalPlanetType())
                    && player.getPlanetsAllianceMode().contains(planet)
                    && FoWHelper.playerHasUnitsOnPlanet(player, game.getTileFromPlanet(planet), planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);

                String msg = player.getRepresentation() + ", due to your exhausting of "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ", you may resolve your _Policy - The Environment: Plunder ➖_ ability."
                        + " Once per action, after you explore a hazardous planet, you may remove 1 unit from that planet to explore that planet.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                Button remove = Buttons.red(
                        "getDamageButtons_" + game.getTileFromPlanet(planet).getPosition() + "_remove",
                        "Remove units in " + game.getTileFromPlanet(planet).getRepresentationForButtons(game, player));
                buttons.add(remove);
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                planetButtons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve Remove", buttons);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), "Resolve Explore", planetButtons);
                player.setHasUsedEnvironmentPlunderAbility(true);
            }
        }
    }

    public static boolean canBePillaged(Player player, Game game, int tg) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return false;
        }
        if (player.getPromissoryNotesInPlayArea().contains("sigma_promise_of_protection")) {
            return false;
        }
        if (Helper.getPlayerFromAbility(game, "pillage") != null
                && !Helper.getPlayerFromAbility(game, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            return tg > 2 && player.getNeighbouringPlayers(true).contains(pillager);
        }
        return false;
    }

    @ButtonHandler("meteorSlings_")
    public static void meteorSlings(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String msg = player.getRepresentation() + " cancelled one BOMBARDMENT hit to place one infantry on "
                + Helper.getPlanetRepresentation(planet, game);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 inf " + planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static void pillageCheck(Player player) {
        if (player != null) pillageCheck(player, player.getGame());
    }

    @ButtonHandler("fakeHiredGuns")
    public static void fakeHiredGuns(Player player, Game game, ButtonInteractionEvent event) {
        if (game.getActivePlayer() == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The bot does not know who the active player is.");
            return;
        }
        if (game.getActivePlayer() == player) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The bot thinks you are the active player. You cannot sell ships to yourself.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("startHiredGuns", "Sell Ships", FactionEmojis.nokar));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " you are about to sell ships to  "
                        + game.getActivePlayer().getRepresentationNoPing()
                        + ". Please do not do this until they finish moving, or else the buttons will get messy. "
                        + "Press the button to confirm that you wish to sell to this player and that they have finished moving. ",
                buttons);
    }

    @ButtonHandler("startHiredGuns")
    public static void startHiredGuns(Player player, Game game, ButtonInteractionEvent event) {
        CommanderUnlockCheckService.checkPlayer(player, "nokar");
        ButtonHelper.deleteTheOneButton(event);
        game.setStoredValue(
                "hiredGunsInPlay",
                player.getFaction() + "_" + game.getActivePlayer().getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " is using their **Hired Guns** ability to send up to three ships to the active system to fight under the command of "
                        + game.getActivePlayer().getRepresentation()
                        + ".\nWhen the tactical action concludes, any of the sold ships in the active system will be converted into the active players ships.");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose up to three ships.",
                TacticalActionService.getTilesToMoveFrom(player, game, event));
    }

    @ButtonHandler("startSimultaneousTacticalAction")
    public static void startSimultaneousTacticalAction(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        if (game.getActivePlayer() == null
                || game.getActivePlayer() == player
                || !player.getAllianceMembers().contains(game.getActivePlayer().getFaction())) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Could not find a correct active player, please resolve manually.");
            return;
        }
        game.setStoredValue(
                "allianceModeSimultaneousAction",
                player.getFaction() + "_" + game.getActivePlayer().getFaction());
        ButtonHelperTacticalAction.selectActiveSystem(player, game, event, "ringTile_" + game.getActiveSystem());

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " is doing a simultaneous tactical action with "
                        + game.getActivePlayer().getRepresentation() + ".");
        // MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + " use
        // buttons to move ships", TacticalActionService.getTilesToMoveFrom(player, game, event));
    }

    public static void pillageCheck(Player player, Game game) {
        if (canBePillaged(player, game, player.getTg())) {
            Player pillager = Helper.getPlayerFromAbility(game, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            List<Button> buttons = new ArrayList<>();
            String playerIdent = player.getRepresentationNoPing();
            player.getDisplayName();
            MessageChannel channel = game.getMainGameChannel();
            if (game.isFowMode()) {
                playerIdent = capitalize(player.getColor());
                channel = pillager.getPrivateChannel();
            }
            String message = pillager.getRepresentationUnfogged() + " you may have the opportunity to **Pillage** "
                    + playerIdent
                    + ". Please check this is a valid **Pillage** opportunity, and use buttons to resolve.";
            buttons.add(Buttons.red(
                    finChecker + "pillage_" + player.getColor() + "_unchecked",
                    "Pillage " + (game.isFowMode() ? playerIdent : player.getFlexibleDisplayName())));
            buttons.add(Buttons.green(finChecker + "deleteButtons", "Decline Pillage Window"));
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    @ButtonHandler("mantleCrack_")
    public static void mantleCracking(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        planet.addToken(Constants.GLEDGE_CORE_PNG);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor() + " Cracked the Mantle of "
                        + Helper.getPlanetRepresentation(planetName, game) + " and gained 4 trade goods (" + oldTg
                        + "->"
                        + player.getTg() + ").\n-# This is technically an optional gain.");
        pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 4);
        List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Game game) {
        List<Button> techToGain = new ArrayList<>();
        for (String tech : currentList) {
            if ((game.isOrdinianC1Mode() || game.isLiberationC4Mode())
                    && Mapper.getTech(tech).isFactionTech()) {
                continue;
            }
            techToGain.add(Buttons.green(
                    "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }

    public static List<String> getPossibleTechForNekroToGainFromPlayer(
            Player nekro, Player victim, List<String> currentList, Game game) {
        List<String> techToGain = new ArrayList<>(currentList);
        for (String tech : victim.getTechs()) {
            if (!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)) {
                techToGain.add(tech);
            }
        }
        return techToGain;
    }

    @ButtonHandler("removeSleeperFromPlanet_")
    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = player.getFactionEmojiOrColor() + " removed a Sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planet, player);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("replaceSleeperWith_")
    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        SleeperTokenHelper.addOrRemoveSleeper(event, game, planetName, player);

        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        if ("mech".equalsIgnoreCase(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + planetName + ", inf " + planetName);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on "
                    + Helper.getPlanetRepresentation(planetName, game) + " with a " + UnitEmojis.mech + " and "
                    + UnitEmojis.infantry;
        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + planetName);
            message = player.getFactionEmojiOrColor() + " replaced a Sleeper on "
                    + Helper.getPlanetRepresentation(planetName, game) + " with a " + UnitEmojis.pds;
            CommanderUnlockCheckService.checkPlayer(player, "titans");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("addProtocol_")
    public static void addProtocol(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String protocol = buttonID.split("_")[1];
        if (!player.hasAbility("protocol_" + protocol)) {
            player.addAbility("protocol_" + protocol);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " gained the _" + capitalize(protocol) + "_ Protocol.");
            event.getMessage().delete().queue();
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you already had the _" + capitalize(protocol) + "_ Protocol.");
        }
    }

    private static List<Button> getAvailableProtocols(Player player) {
        List<Button> buttons = new ArrayList<>();
        String protocol = "distribution";
        if (!player.hasAbility("protocol_" + protocol)) {
            buttons.add(Buttons.gray("addProtocol_" + protocol, capitalize(protocol)));
        } else {
            player.removeAbility("protocol_" + protocol);
        }
        protocol = "command";
        if (!player.hasAbility("protocol_" + protocol)) {
            buttons.add(Buttons.gray("addProtocol_" + protocol, capitalize(protocol)));
        } else {
            player.removeAbility("protocol_" + protocol);
        }
        protocol = "excavation";
        if (!player.hasAbility("protocol_" + protocol)) {
            buttons.add(Buttons.gray("addProtocol_" + protocol, capitalize(protocol)));
        } else {
            player.removeAbility("protocol_" + protocol);
        }
        protocol = "espionage";
        if (!player.hasAbility("protocol_" + protocol)) {
            buttons.add(Buttons.gray("addProtocol_" + protocol, capitalize(protocol)));
        } else {
            player.removeAbility("protocol_" + protocol);
        }
        protocol = "conflict";
        if (!player.hasAbility("protocol_" + protocol)) {
            buttons.add(Buttons.gray("addProtocol_" + protocol, capitalize(protocol)));
        } else {
            player.removeAbility("protocol_" + protocol);
        }

        return buttons;
    }

    public static void giveKeleresCommsNTg(Game game, GenericInteractionCreateEvent event) {
        if (game.isMinorFactionsMode()) {
            for (Tile tile : game.getTileMap().values()) {
                if (!tile.isHomeSystem(game)) {
                    for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                        Planet p = (Planet) unitHolder;
                        if (p.getPlanetModel().getPlanetTypes().contains(PlanetType.FACTION)) {
                            unitHolder.addToken("attachment_threetraits.png");
                        }
                    }
                }
            }
        }
        if (game.isDangerousWildsMode()) {
            Player neutral = game.getPlayerFromColorOrFaction("neutral");
            if (neutral == null) {
                List<String> unusedColors =
                        game.getUnusedColors().stream().map(ColorModel::getName).toList();
                String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
                game.setupNeutralPlayer(color);
                neutral = game.getPlayerFromColorOrFaction("neutral");
            }

            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                    if (ButtonHelper.getTypeOfPlanet(game, uH.getName()).contains("hazardous")) {
                        boolean owned = false;
                        for (Player p2 : game.getRealPlayers()) {
                            if (p2.getPlanets().contains(uH.getName())) {
                                owned = true;
                                break;
                            }
                        }
                        if (!owned) {
                            int resource = Helper.getPlanetResources(uH.getName(), game);
                            int neutralUnitsToAdd = resource - uH.getUnitCount(UnitType.Infantry, neutral);
                            if (neutralUnitsToAdd > 0) {
                                AddUnitService.addUnits(
                                        event,
                                        tile,
                                        game,
                                        neutral.getColor(),
                                        neutralUnitsToAdd + " infantry " + uH.getName());
                            }
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Added neutral infantry to hazardous planets");
        }
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("divination")) {
                rollOmenDiceAtStartOfStrat(game, player);
            }
            if (player.hasAbility("protocols")) {
                List<Button> buttons = getAvailableProtocols(player);
                String sb = player.getRepresentationUnfogged() + ", your **Protocols** ability was triggered."
                        + " You can now select two Protocols that you did not select last round to be your active Protocols, and give your leaders abilities.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", please choose your first Protocol.",
                        buttons);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", please choose your second Protocol.",
                        buttons);
            }

            if (!player.hasAbility("council_patronage")) continue;
            ButtonHelperStats.gainTGs(event, game, player, 1, true);
            String sb = player.getRepresentationUnfogged() + " your **Council Patronage** ability was triggered. Your "
                    + MiscEmojis.comm + " commodities have been replenished and you have gained 1 "
                    + MiscEmojis.getTGorNomadCoinEmoji(game) + " trade good ("
                    + (player.getTg() - 1) + " -> " + player.getTg() + ")";
            ButtonHelperStats.replenishComms(event, game, player, true);

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
        }
    }

    @ButtonHandler("starforgeTile_")
    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("starforgeTile_", "");

        String prefix = player.getFinsFactionCheckerPrefix() + "starforge_";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(prefix + "destroyer_" + pos, "Starforge Destroyer", UnitEmojis.destroyer));
        buttons.add(Buttons.red(prefix + "fighters_" + pos, "Starforge 2 Fighters", UnitEmojis.fighter));
        String message = "Please choose what units you wish to **Starforge**.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("starforge_")
    public static void starforge(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String successMessage;
        if (player.getStrategicCC() > 0) {
            successMessage = player.getRepresentationUnfogged() + " spent 1 strategy token ("
                    + (player.getStrategicCC()) + " -> " + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(
                    player, game, event, FactionEmojis.Muaat + " **Starforge**'d");
        } else {
            player.addExhaustedRelic("emelpar");
            successMessage =
                    player.getRepresentationUnfogged() + " exhausted the _" + RelicHelper.sillySpelling() + "_.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);

        List<Button> buttons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        if ("destroyer".equals(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 destroyer");
            successMessage = "Produced 1 " + UnitEmojis.destroyer + " in tile "
                    + tile.getRepresentationForButtons(game, player) + ".";

        } else {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "2 ff");
            successMessage = "Produced 2 " + UnitEmojis.fighter + " in tile "
                    + tile.getRepresentationForButtons(game, player) + ".";
        }
        if ((player.ownsUnit("muaat_mech")
                        || player.ownsUnit("sigma_muaat_mech")
                        || player.ownsUnit("absol_muaat_mech"))
                && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, game, player, event);
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("mitoMechPlacement_")
    public static void resolveMitosisMechPlacement(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String uH = buttonID.split("_")[2];
        String reason = buttonID.split("_")[3];
        switch (reason) {
            case "mitosis" -> reason = "**Mitosis**";
            case "refit" -> reason = "_Refit Troops_";
        }
        String successMessage;
        if ("space".equalsIgnoreCase(uH)) {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech in the space area of "
                    + tile.getRepresentationForButtons(game, player) + " with " + reason + ".";
        } else {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech on "
                    + Helper.getPlanetRepresentation(uH, game) + " with " + reason + ".";
        }
        UnitHolder holder = tile.getUnitHolders().get(uH);
        MoveUnitService.replaceUnit(event, game, player, tile, holder, UnitType.Infantry, UnitType.Mech);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getXxchaPeaceAccordsButtons(
            Game game, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = game.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false)) {
                Tile tile2 = game.getTileByPosition(pos2);
                for (Planet planetUnit2 : tile2.getPlanetUnitHolders()) {
                    String planet2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!player.getPlanetsAllianceMode().contains(planet2)
                            && !planetRepresentation2.contains("Mecatol")
                            && (planetUnit2.getUnits() == null
                                    || planetUnit2.getUnits().isEmpty())
                            && !planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(
                                finChecker + "peaceAccords_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
                        planetsChecked.add(planet2);
                    }
                }
            }
        }
        return buttons;
    }

    public static List<Button> getKyroContagionButtons(
            Game game, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = game.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                Tile tile2 = game.getTileByPosition(pos2);
                for (Planet planetUnit2 : tile2.getPlanetUnitHolders()) {
                    String planet2 = planetUnit2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                    if (!planetsChecked.contains(planet2)) {
                        buttons.add(Buttons.green(
                                finChecker + "contagion_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
                        planetsChecked.add(planet2);
                    }
                }
            }
            for (Planet planetUnit2 : tile.getPlanetUnitHolders()) {
                String planet2 = planetUnit2.getName();
                String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
                if (!planetsChecked.contains(planet2)) {
                    buttons.add(Buttons.green(
                            finChecker + "contagion_" + planet2, planetRepresentation2, FactionEmojis.Xxcha));
                    planetsChecked.add(planet2);
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("moult_")
    public static void resolveMoult(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        String pos = buttonID.split("_")[1];
        String generalMsg = player.getFactionEmoji()
                + " is resolving **Moult** (after winning the space combat) to build 1 ship, reducing the cost by 1 for each of their non-fighter ships destroyed in the combat.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), generalMsg);
        String type = "sling";
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce a ship. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("munitionsReserves")
    public static void munitionsReserves(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", you only have " + player.getTg()
                            + " trade goods and thus can't use **Munitions Reserve**.");
            return;
        }
        String msg = player.getFactionEmoji() + " spent 2 trade goods " + player.gainTG(-2)
                + " to use **Munitions Reserves**."
                + "\nTheir next roll will automatically reroll misses. If they wish to instead reroll hits as a part of a deal, they should just ignore the rerolls.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        game.setStoredValue("munitionsReserves", player.getFaction());
    }

    @ButtonHandler("virTraining")
    public static void virTraining(ButtonInteractionEvent event, Game game, Player player) {
        String msg = player.getFactionEmoji()
                + " is using their _V.I.R. Training_ technology to cancel one hit they produced in order to cancel up to 1 hit their opponent produced. "
                + "They can do this once per round of combat. Both sides should just manually assign one fewer hits.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("contagion_")
    public static void lastStepOfContagion(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String amount = "1";
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));

        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " inf " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " used their **Contagion** ability to land " + amount + " infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
        if (players.size() > 1) {
            StartCombatService.startGroundCombat(players.get(0), players.get(1), game, event, unitHolder, tile);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("peaceAccords_")
    public static void resolvePeaceAccords(String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        if ("lockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "mallice";
            Tile tile = game.getTileFromPlanet("lockedmallice");
            FlipTileService.flipTileIfNeeded(event, tile, game);
        } else if ("hexlockedmallice".equalsIgnoreCase(planetID)) {
            planetID = "hexmallice";
            Tile tile = game.getTileFromPlanet("hexlockedmallice");
            FlipTileService.flipTileIfNeeded(event, tile, game);
        }
        AddPlanetService.addPlanet(player, planetID, game, event, false);
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game);
        String msg = player.getFactionEmojiOrColor() + " claimed the planet " + planetRep + " using the "
                + FactionEmojis.Xxcha + "**Peace Accords** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("initialIndoctrination_")
    public static void resolveInitialIndoctrinationQuestion(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Buttons.green("indoctrinate_" + planet + "_infantry", "Indoctrinate 1 Infantry into 1 Infantry")
                .withEmoji(UnitEmojis.infantry.asEmoji()));
        if (player.hasUnit("yin_mech")) {
            options.add(Buttons.green("indoctrinate_" + planet + "_mech", "Indoctrinate 1 Infantry into 1 Mech")
                    .withEmoji(UnitEmojis.mech.asEmoji()));
        }
        options.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " use buttons to resolve **Indoctrination**.",
                options);
    }

    @ButtonHandler("indoctrinate_")
    public static void resolveFollowUpIndoctrinationQuestion(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit + " " + planet);
        String opponent = "their opponent's";
        String colour = "";
        for (Player p2 : game.getPlayers().values()) {
            if (p2.getColor() == null
                    || p2 == player
                    || player.getAllianceMembers().contains(p2.getFaction())) {
                continue; // fix indoctrinate vs neutral
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)
                    && !player.getAllianceMembers().contains(p2.getFaction())) {
                RemoveUnitService.removeUnits(event, tile, game, p2.getColor(), "1 infantry " + planet);
                ButtonHelper.resolveInfantryRemoval(p2, 1);
                opponent = p2.getRepresentationNoPing();
                colour = p2.getColor();
                break;
            }
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        CommanderUnlockCheckService.checkPlayer(player, "yin");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getFactionEmoji() + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                            + Helper.getPlanetRepresentation(planet, game) + " using **Indoctrination**.");
        } else if (RandomHelper.isOneInX(100) && !colour.isEmpty() && "infantry".equals(unit)) {
            String poem = "";
            switch (ThreadLocalRandom.current().nextInt(20)) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    poem += "\\> Roses are red\n";
                    break;
                case 10:
                case 11:
                case 12:
                case 13:
                    poem += "\\> Violets are blue\n";
                    break;
                case 14:
                    poem += "\\> Violets are purple\n";
                    break;
                case 15:
                    poem += "\\> Daffodils are yellow\n";
                    break;
                case 16:
                    poem += "\\> Daisies are white\n";
                    break;
                case 17:
                    poem += "\\> Marigolds are orange\n";
                    break;
                case 18:
                    poem += "\\> Carnations are pink\n";
                    break;
                case 19:
                    poem += "\\> Lilacs are lilac\n";
                    break;
            }
            poem += "\\> Infantry are " + ColorEmojis.getColorEmojiWithName(colour) + "\n";
            poem += "\\> Wololo " + MiscEmojis.Wololo + "\n";
            poem += "\\> Now infantry are " + ColorEmojis.getColorEmojiWithName(player.getColor()) + "\n";
            poem += "-# Yes, it doesn't rhyme (probably), but there's like, "
                    + Mapper.getColors().size()
                    + " available colours, and I'm not writing a unique poem for every possible pair.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), poem);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " replaced 1 of " + opponent + " infantry with 1 " + unit
                            + " on " + Helper.getPlanetRepresentation(planet, game) + " using **Indoctrination**.");
        }
        options.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please pay for **Indoctrination**.",
                options);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("distant_suns_")
    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their **Distant Suns** ability.";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ExploreService.explorePlanet(
                    event, game.getTileFromPlanet(info[1]), info[1], info[2], player, true, game, 1, false);
        } else {
            ExploreService.explorePlanet(
                    event, game.getTileFromPlanet(info[1]), info[1], info[2], player, true, game, 2, false);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("deep_mining_")
    public static void deepMining(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String bID = buttonID.replace("deep_mining_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = player.getFactionEmoji() + " declined to use their **Deep Mining** ability.";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ExploreService.explorePlanet(
                    event, game.getTileFromPlanet(info[1]), info[1], info[2], player, true, game, 1, false);
        } else {
            message = player.getFactionEmoji() + " used their **Deep Mining** ability to gain 1 trade good "
                    + player.gainTG(1) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            pillageCheck(player, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }

        event.getMessage().delete().queue();
    }

    @ButtonHandler("augersPeak_")
    public static void handleAugursPeak(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
            ObjectiveHelper.secondHalfOfPeakStage1(game, player, 1);
        } else {
            ObjectiveHelper.secondHalfOfPeakStage2(game, player, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("initialPeak")
    public static void handleAugursPeakInitial(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("augersPeak_1", "Peek At Next Stage 1"));
        buttons.add(Buttons.green("augersPeak_2", "Peek At Next Stage 2"));
        String msg = player.getRepresentationUnfogged()
                + " the bot doesn't know if the next objective is a stage 1 or a stage 2. Please help it out and click the right button.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    private static void availynStep1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String destination = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        for (Tile tile2 : game.getTileMap().values()) {
            if (tile2.getPosition().equalsIgnoreCase(destination)) {
                continue;
            }
            UnitHolder unitHolder = tile2.getUnitHolders().get(Constants.SPACE);
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                buttons.add(Buttons.green(
                        "availynStep2_" + destination + "_" + tile2.getPosition(),
                        tile2.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        String msg = player.getRepresentation() + ", please choose the system you wish to pull fighters from.";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("availynStep2_")
    public static void availynStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String destination = buttonID.split("_")[1];
        String origin = buttonID.split("_")[2];
        Tile orig = game.getTileByPosition(origin);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("availynStep3_" + destination + "_" + origin + "_1", "1 fighter"));
        if (orig.getUnitHolders().get("space").getUnitCount(UnitType.Fighter, player.getColor()) > 1) {
            buttons.add(Buttons.green("availynStep3_" + destination + "_" + origin + "_2", "2 fighters"));
        }
        if (orig.getUnitHolders().get("space").getUnitCount(UnitType.Fighter, player.getColor()) > 2) {
            buttons.add(Buttons.green("availynStep3_" + destination + "_" + origin + "_3", "3 fighters"));
        }
        String msg = player.getRepresentation() + ", please choose whether to pull 1 or 2 or 3 fighters.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("availynStep3_")
    public static void availynStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String destination = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(destination);
        String origin = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(origin);
        String fighters = buttonID.split("_")[3];

        RemoveUnitService.removeUnits(event, tile2, game, player.getColor(), fighters + " fighters");
        AddUnitService.addUnits(event, tile, game, player.getColor(), fighters + " fighters");
        String msg = player.getRepresentation() + " used the _Availyn_ Superweapon ability and transferred " + fighters
                + " fighter" + ("1".equals(fighters) ? "" : "s") + " from "
                + tile2.getRepresentationForButtons(game, player) + " to "
                + tile.getRepresentationForButtons(game, player) + ".";
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }
}
