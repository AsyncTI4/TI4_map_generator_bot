package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.DreamButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronFactionTechsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.tech.BastionTechService;
import ti4.service.unit.CheckUnitContainmentService;

/** Builds the established general combat button catalog without sending Discord messages. */
@UtilityClass
class CombatV2StartButtons {

    static List<Button> getGeneralCombatButtons(Game game, String pos, Player p1, Player p2, String groundOrSpace) {
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getUnitHolders().get("space");
        boolean isSpaceCombat = "space".equalsIgnoreCase(groundOrSpace);
        boolean isGroundCombat = "ground".equalsIgnoreCase(groundOrSpace);

        if ("justPicture".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Buttons.blue(
                    "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
                    "Refresh Picture"));
            return buttons;
        }
        buttons.add(Buttons.red("getDamageButtons_" + pos + "_" + groundOrSpace + "combat", "Assign Hits"));
        if (p1.isDummy() || p1.isNpc()) {
            buttons.add(Buttons.red(
                    p1.dummyPlayerSpoof() + "getDamageButtons_" + pos + "_" + groundOrSpace + "combat",
                    "Assign Hits For Dummy"));
        }
        if (p2.isDummy() || p2.isNpc()) {
            buttons.add(Buttons.red(
                    p2.dummyPlayerSpoof() + "getDamageButtons_" + pos + "_" + groundOrSpace + "combat",
                    "Assign Hits For Dummy"));
        }
        buttons.add(Buttons.gray("checkCombatACs", "Check Combat Action Cards", CardEmojis.getACEmoji(game)));
        buttons.add(Buttons.green("getRepairButtons_" + pos, "Repair Damage"));
        buttons.add(Buttons.blue(
                "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
                "Refresh Picture"));
        checkAndAddIncomprehensibleFormButton(game, p1, p2, isSpaceCombat, tile, buttons);

        if (p1.hasTechReady("sc") || (!game.isFowMode() && p2.hasTechReady("sc"))) {
            if (p1.hasTechReady("sc")) {
                buttons.add(Buttons.green(
                        p1.factionButtonChecker() + "applytempcombatmod__" + "tech" + "__" + "sc",
                        "Use Supercharge",
                        FactionEmojis.Naaz));
            }
            if (!game.isFowMode() && p2.hasTechReady("sc")) {
                buttons.add(Buttons.green(
                        p2.factionButtonChecker() + "applytempcombatmod__" + "tech" + "__" + "sc",
                        "Use Supercharge",
                        FactionEmojis.Naaz));
            }
        }
        if (p1.hasTechReady("beironats") || (!game.isFowMode() && p2.hasTechReady("beironats"))) {
            if (p1.hasTechReady("beironats")) {
                IronFactionTechsHandler.addAdvancedTargetingSystemsButton(buttons, game, p1, p2, pos, groundOrSpace);
            }
            if (!game.isFowMode() && p2.hasTechReady("beironats")) {
                IronFactionTechsHandler.addAdvancedTargetingSystemsButton(buttons, game, p2, p1, pos, groundOrSpace);
            }
        }

        checkAndAddSubatomicButton(game, p1, isSpaceCombat, tile, buttons);
        checkAndAddSubatomicButton(game, p2, isSpaceCombat, tile, buttons);

        checkAndAddFractalPlatingButton(game, p1, isSpaceCombat, tile, buttons);
        checkAndAddFractalPlatingButton(game, p2, isSpaceCombat, tile, buttons);

        checkAndAddDihmonBreakthroughButton(p1, isSpaceCombat, buttons, tile);
        if (!game.isFowMode()) checkAndAddDihmonBreakthroughButton(p2, isSpaceCombat, buttons, tile);

        for (Player agentHolder : game.getRealPlayers()) {
            String factionChecker = "FFCC_" + agentHolder.getFaction() + "_";

            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("titansagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "exhaustAgent_titansagent",
                        "Use Titans " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Agent",
                        FactionEmojis.Titans));
            }
            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasRelicReady("heartofixth")) {
                buttons.add(Buttons.blue(
                        factionChecker + "exhaustRelic_heartofixth",
                        "Exhaust Heart of Ixth",
                        agentHolder.getFactionEmoji()));
            }
            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("gheminaagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "exhaustAgent_gheminaagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Ghemina Agents",
                        FactionEmojis.ghemina));
            }

            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("kjalengardagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "exhaustAgent_kjalengardagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Kjalengard Agent",
                        FactionEmojis.kjalengard));
            }

            if ((!game.isFowMode() || agentHolder == p1)
                    && agentHolder.hasUnexhaustedLeader("solagent")
                    && isGroundCombat) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_solagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Sol Agent",
                        FactionEmojis.Sol));
            }
            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("bastionagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_bastionagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Bastion Agent",
                        FactionEmojis.Bastion));
            }

            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("valiantagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_valiantagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Valiant Genome",
                        FactionEmojis.Bastion));
            }
            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("obsidianagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_obsidianagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Obsidian Agent",
                        FactionEmojis.Obsidian));
            }

            if ((!game.isFowMode() || agentHolder == p1)
                    && agentHolder.hasUnexhaustedLeader("kyroagent")
                    && isGroundCombat) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_kyroagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Kyro Agent",
                        FactionEmojis.kyro));
            }

            if ((!game.isFowMode() || agentHolder == p1)
                    && agentHolder.hasUnexhaustedLeader("letnevagent")
                    && "space".equalsIgnoreCase(groundOrSpace)) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_letnevagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Letnev Agent",
                        FactionEmojis.Letnev));
            }
            if ((!game.isFowMode() || agentHolder == p1)
                    && agentHolder.hasUnexhaustedLeader("xanagent")
                    && "space".equalsIgnoreCase(groundOrSpace)) {
                buttons.add(Buttons.gray(
                        factionChecker + "getAgentSelection_xanagent",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Noro Weba",
                        FactionEmojis.xan));
            }

            if ((!game.isFowMode() || agentHolder == p1) && agentHolder.hasUnexhaustedLeader("nomadagentthundarian")) {
                buttons.add(Buttons.gray(
                        factionChecker + "exhaustAgent_nomadagentthundarian",
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "The Thundarian",
                        FactionEmojis.Nomad));
            }
            List<Tile> flagshipTile =
                    CheckUnitContainmentService.getTilesContainingPlayersUnits(game, agentHolder, UnitType.Flagship);
            if (agentHolder.hasUnit("empyrean_flagship")
                    && !flagshipTile.isEmpty()
                    && FoWHelper.getAdjacentTiles(game, pos, agentHolder, false, true)
                            .contains(flagshipTile.getFirst().getPosition())) {
                buttons.add(Buttons.gray(
                        factionChecker + "empyreanFlagshipAbilityStep1_" + pos,
                        "Use Empyrean Flagship Ability",
                        agentHolder.getFactionEmojiOrColor()));
            }

            if ((!game.isFowMode() || agentHolder == p1)
                    && (isSpaceCombat || !game.isTwilightsFallMode())
                    && agentHolder.hasUnexhaustedLeader("yinagent")) {
                buttons.add(Buttons.gray(
                        factionChecker + "yinagent_" + pos,
                        "Use " + (agentHolder.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                + "Yin Agent",
                        FactionEmojis.Yin));
            }
            if ((!game.isFowMode() || agentHolder == p1)
                    && ButtonHelper.doesPlayerHaveFSHere("mirveda_flagship", agentHolder, tile)
                    && isSpaceCombat) {
                buttons.add(Buttons.gray(
                        factionChecker + "mirvedaFS_" + pos,
                        "Place Fighter (Mirveda Flagship)",
                        FactionEmojis.mirveda));
            }
            if ((!game.isFowMode() || agentHolder == p1)
                    && ButtonHelper.doesPlayerHaveFSHere("belkosea_flagship", agentHolder, tile)
                    && isSpaceCombat) {
                buttons.add(Buttons.gray(
                        factionChecker + "becomeDamaged_" + pos + "_flagship",
                        "Become Damaged To Produce Hit (Belkosea Flagship)",
                        FactionEmojis.belkosea));
            }
            if ((!game.isFowMode() || agentHolder == p1)
                    && ButtonHelper.doesPlayerHaveFSHere("kortali_flagship", agentHolder, tile)
                    && isSpaceCombat) {
                buttons.add(Buttons.gray(
                        factionChecker + "becomeDamaged_" + pos + "_flagship",
                        "Become Damaged Upon Win To Gain Command Token (Kortali Flagship)",
                        FactionEmojis.kortali));
            }
        }

        // Exo 2s
        if ("space".equalsIgnoreCase(groundOrSpace) && !game.isFowMode()) {
            if ((tile.getSpaceUnitHolder().getUnitCount(Units.UnitType.Dreadnought, p1.getColor()) > 0
                            && (p1.hasTech("exo2") || p1.hasUnit("tf-exotrireme")))
                    || (tile.getSpaceUnitHolder().getUnitCount(Units.UnitType.Dreadnought, p2.getColor()) > 0
                            && (p2.hasTech("exo2") || p2.hasUnit("tf-exotrireme")))) {
                buttons.add(Buttons.blue(
                        "assCannonNDihmohn_exo_" + tile.getPosition(),
                        "Use Exotrireme II Ability",
                        FactionEmojis.Sardakk));
            }
        }
        if ("space".equalsIgnoreCase(groundOrSpace)) {
            if (p1.hasUnlockedBreakthrough("letnevbt")) {
                buttons.add(Buttons.blue(
                        p1.factionButtonChecker() + "assignGravleash_" + tile.getPosition(),
                        "Assign Gravleash Maneuvers",
                        FactionEmojis.Letnev));
            }
            if (p2.hasUnlockedBreakthrough("letnevbt") && !game.isFowMode()) {
                buttons.add(Buttons.blue(
                        p2.factionButtonChecker() + "assignGravleash_" + tile.getPosition(),
                        "Assign Gravleash Maneuvers",
                        FactionEmojis.Letnev));
            }
        }
        if (p1.hasUnlockedBreakthrough("sardakkbt")) {
            buttons.add(Buttons.gray(
                    p1.factionButtonChecker() + "sardakkbtRes",
                    "Resolve Sardakk Breakthrough (Upon Win)",
                    FactionEmojis.Sardakk));
        }
        if (p1.hasUnit("pinktf_mech")
                && isGroundCombat
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Mech)
                        .contains(tile)) {
            buttons.add(Buttons.gray(
                    p1.factionButtonChecker() + "drawSingularNewSpliceCard_units_pinktfmech",
                    "Draw 1 Unit Upgrade (Upon Win)",
                    FactionEmojis.pinktf));
        }

        if (p1.hasUnit("orangetf_mech")
                && isGroundCombat
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Mech)
                        .contains(tile)
                && p1.getStrategicCC() > 0) {
            buttons.add(Buttons.gray(
                    p1.factionButtonChecker() + "orangeTFMechRepair",
                    "Spend Strat CC to Repair Mechs",
                    FactionEmojis.orangetf));
        }

        if (p2.hasUnit("orangetf_mech")
                && isGroundCombat
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, UnitType.Mech)
                        .contains(tile)
                && p2.getStrategicCC() > 0
                && !game.isFowMode()) {
            buttons.add(Buttons.gray(
                    p2.factionButtonChecker() + "orangeTFMechRepair",
                    "Spend Strat CC to Repair Mechs",
                    FactionEmojis.orangetf));
        }

        if (p2.hasUnlockedBreakthrough("sardakkbt") && !game.isFowMode()) {
            buttons.add(Buttons.gray(
                    p2.factionButtonChecker() + "sardakkbtRes",
                    "Resolve Sardakk Breakthrough (Upon Win)",
                    FactionEmojis.Sardakk));
        }
        if (p2.hasUnit("pinktf_mech")
                && isGroundCombat
                && !game.isFowMode()
                && ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, UnitType.Mech)
                        .contains(tile)) {
            buttons.add(Buttons.gray(
                    p2.factionButtonChecker() + "drawSingularNewSpliceCard_units_pinktfmech",
                    "Draw 1 Unit Upgrade (Upon Win)",
                    FactionEmojis.pinktf));
        }
        if (p1.hasAbility("data_recovery") && p1 != game.getActivePlayer()) {
            buttons.add(Buttons.gray(
                    p1.factionButtonChecker() + "dataRecovery_" + p2.getColor(),
                    "Grab 1 Control Token (Upon Unit Death)",
                    FactionEmojis.qhet));
        }
        if (p2.hasAbility("data_recovery") && p2 != game.getActivePlayer()) {
            buttons.add(Buttons.gray(
                    p2.factionButtonChecker() + "dataRecovery_" + p1.getColor(),
                    "Grab 1 Control Token (Upon Unit Death)",
                    FactionEmojis.qhet));
        }
        if ((p2.hasUnexhaustedLeader("kortaliagent"))
                && !game.isFowMode()
                && isGroundCombat
                && !p1.getFragments().isEmpty()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustAgent_kortaliagent_" + p1.getColor(),
                    "Use Kortali Agent",
                    FactionEmojis.kortali));
        }
        if (p1.hasUnexhaustedLeader("kortaliagent")
                && isGroundCombat
                && !p2.getFragments().isEmpty()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustAgent_kortaliagent_" + p2.getColor(),
                    "Use Kortali Agent",
                    FactionEmojis.kortali));
        }

        if ((p2.hasAbility("glory")) && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            if (!ButtonHelperAgents.getGloryTokensLeft(game).isEmpty()) {
                buttons.add(Buttons.gray(
                        factionChecker + "placeGlory_" + pos,
                        "Place Glory Token (Upon Win)",
                        FactionEmojis.kjalengard));
            } else {
                buttons.add(Buttons.gray(
                        factionChecker + "moveGloryStart_" + pos,
                        "Move Glory Token (Upon Win)",
                        FactionEmojis.kjalengard));
            }
            if (p2.getStrategicCC() > 0) {
                buttons.add(Buttons.gray(
                        factionChecker + "gloryTech", "Research Unit Upgrade (Upon Win)", FactionEmojis.kjalengard));
            }
        }
        if (p1.hasAbility("glory")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            if (!ButtonHelperAgents.getGloryTokensLeft(game).isEmpty()) {
                buttons.add(Buttons.gray(
                        factionChecker + "placeGlory_" + pos,
                        "Place Glory Token (Upon Win)",
                        FactionEmojis.kjalengard));
            } else {
                buttons.add(Buttons.gray(
                        factionChecker + "moveGloryStart_" + pos,
                        "Move Glory Token (Upon Win)",
                        FactionEmojis.kjalengard));
            }
            if (p1.getStrategicCC() > 0) {
                buttons.add(Buttons.gray(
                        factionChecker + "gloryTech", "Research Unit Upgrade (Upon Win)", FactionEmojis.kjalengard));
            }
        }
        if ((p2 == game.getActivePlayer() && p2.hasAbility("pride")) && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "resolvePride_" + p1.getFaction(),
                    "Resolve Pride (Upon Win)",
                    FactionEmojis.toldar));
        }
        if ((p1 == game.getActivePlayer() && p1.hasAbility("pride"))) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "resolvePride_" + p2.getFaction(),
                    "Resolve Pride (Upon Win)",
                    FactionEmojis.toldar));
        }

        if ((p2.hasAbility("collateralized_loans"))
                && !game.isFowMode()
                && p2.getDebtTokenCount(p1.getColor(), Constants.VADEN_DEBT_POOL) > 0
                && "space".equalsIgnoreCase(groundOrSpace)) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "collateralizedLoans_" + pos + "_" + p1.getFaction(),
                    "Collateralized Loans",
                    FactionEmojis.vaden));
        }
        if ((p1.hasAbility("collateralized_loans"))
                && p1.getDebtTokenCount(p2.getColor(), Constants.VADEN_DEBT_POOL) > 0
                && "space".equalsIgnoreCase(groundOrSpace)) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "collateralizedLoans_" + pos + "_" + p2.getFaction(),
                    "Collateralized Loans",
                    FactionEmojis.vaden));
        }

        if ((game.playerHasLeaderUnlockedOrAlliance(p1, "belkoseacommander")
                        || game.playerHasLeaderUnlockedOrAlliance(p2, "belkoseacommander"))
                && isSpaceCombat) {
            buttons.add(Buttons.gray(
                    "combatRoll_" + tile.getPosition() + "_space_afb",
                    "Roll ANTI-FIGHTER BARRAGE",
                    FactionEmojis.belkosea));
        }
        if (p2.hasAbility("necrophage") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(factionChecker + "offerNecrophage", "Necrophage", FactionEmojis.mykomentori));
        }
        if (p1.hasAbility("necrophage")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(factionChecker + "offerNecrophage", "Necrophage", FactionEmojis.mykomentori));
        }

        if (p2.getPromissoryNotesInPlayArea().contains("dspntold")
                && !game.isFowMode()
                && p2.getTotalVictoryPoints() < p1.getTotalVictoryPoints()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "toldarPN", "Gain 3 Commodities (Upon Win)", FactionEmojis.toldar));
        }
        if (p1.getPromissoryNotesInPlayArea().contains("dspntold")
                && p1.getTotalVictoryPoints() < p2.getTotalVictoryPoints()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "toldarPN", "Gain 3 Commodities (Upon Win)", FactionEmojis.toldar));
        }

        if (p2.hasRelicReady("superweaponcaled") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustSuperweapon_caled_" + tile.getPosition(),
                    "Destroy 1 Ship With Caled",
                    FactionEmojis.belkosea));
        }
        if (p1.hasRelicReady("superweaponcaled")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustSuperweapon_caled_" + tile.getPosition(),
                    "Destroy 1 Ship With Caled",
                    FactionEmojis.belkosea));
        }

        boolean hasDevotionShips = space != null
                && (space.getUnitCount(Units.UnitType.Destroyer, p2) > 0
                        || space.getUnitCount(Units.UnitType.Cruiser, p2) > 0);
        if (p2.hasAbility("devotion") && !game.isFowMode() && isSpaceCombat && hasDevotionShips) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "startDevotion_" + tile.getPosition(), "Devotion", FactionEmojis.Yin));
        }
        hasDevotionShips = space != null
                && (space.getUnitCount(Units.UnitType.Destroyer, p1) > 0
                        || space.getUnitCount(Units.UnitType.Cruiser, p1) > 0);
        if (p1.hasAbility("devotion") && isSpaceCombat && hasDevotionShips) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "startDevotion_" + tile.getPosition(), "Devotion", FactionEmojis.Yin));
        }

        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2, "mykomentoricommander") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "resolveMykoCommander",
                    "Spend For Myko-Mentori Commander",
                    FactionEmojis.mykomentori));
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1, "mykomentoricommander")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "resolveMykoCommander",
                    "Spend For Myko-Mentori Commander",
                    FactionEmojis.mykomentori));
        }

        if (isSpaceCombat && p2.hasAbility("munitions") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "munitionsReserves", "Use Munitions Reserves", FactionEmojis.Letnev));
        }
        if (isSpaceCombat && p1.hasAbility("munitions")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "munitionsReserves", "Use Munitions Reserves", FactionEmojis.Letnev));
        }

        if (p2.hasTech("dstoldr") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(factionChecker + "virTraining", "Use V.I.R. Training", FactionEmojis.toldar));
        }
        if (p1.hasTech("dstoldr")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(factionChecker + "virTraining", "Use V.I.R. Training", FactionEmojis.toldar));
        }
        if (p2.hasTech("dsvadey") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "vadenYellowTechUse_" + p1.getColor(),
                    "Produce Additional Hit for 1 Trade Good",
                    FactionEmojis.vaden));
        }
        if (p1.hasTech("dsvadey")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "vadenYellowTechUse_" + p2.getColor(),
                    "Produce Additional Hit for 1 Trade Good",
                    FactionEmojis.vaden));
        }
        if (p2.hasTechReady("dsvadey") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustTech_dsvadey", "Exhaust To Kill Sustaining Unit", FactionEmojis.vaden));
        }
        if (p1.hasTechReady("dsvadey")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "exhaustTech_dsvadey", "Exhaust To Kill Sustaining Unit", FactionEmojis.vaden));
        }

        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p2, tile) && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "gain_1_comms_stay",
                    "Gain Commodity with Myko-Mentori Flagship",
                    FactionEmojis.mykomentori));
        }
        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p1, tile)) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "gain_1_comms_stay",
                    "Gain Commodity with Myko-Mentori Flagship",
                    FactionEmojis.mykomentori));
        }

        if ((ButtonHelper.doesPlayerHaveFSHere("sigma_sol_flagship_1", p2, tile)
                        || ButtonHelper.doesPlayerHaveFSHere("sigma_sol_flagship_2", p2, tile))
                && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            String tp = tile.getPosition();
            buttons.add(Buttons.blue(
                    factionChecker + "placeOneNDone_skipbuild_ff_space" + tp,
                    "Sol Flagship Fighter",
                    FactionEmojis.Sol));
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String pp = planet.getName();
                Button inf2Button = Buttons.green(
                        factionChecker + "placeOneNDone_skipbuild_gf_" + pp,
                        "Sol Flagship Infantry on" + Helper.getPlanetRepresentation(pp, game),
                        FactionEmojis.Sol);
                buttons.add(inf2Button);
            }
        }
        if ((ButtonHelper.doesPlayerHaveFSHere("sigma_sol_flagship_1", p1, tile)
                        || ButtonHelper.doesPlayerHaveFSHere("sigma_sol_flagship_2", p1, tile))
                && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            String tp = tile.getPosition();
            buttons.add(Buttons.blue(
                    factionChecker + "placeOneNDone_skipbuild_ff_space" + tp,
                    "Sol Flagship Fighter",
                    FactionEmojis.Sol));
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String pp = planet.getName();
                buttons.add(Buttons.green(
                        factionChecker + "placeOneNDone_skipbuild_gf_" + pp,
                        "Sol Flagship Infantry on" + Helper.getPlanetRepresentation(pp, game),
                        FactionEmojis.Sol));
            }
        }

        if (isSpaceCombat) {
            buttons.add(Buttons.gray("announceARetreat", "Announce A Retreat"));
            buttons.add(Buttons.red("retreat_" + pos, "Retreat"));
        }

        if (!game.isFowMode()) {
            buttons.add(Buttons.gray(
                    "announceReadyForDice_" + p1.getColor() + "_" + p2.getColor(), "Declare Ready To Throw Dice"));
        }
        if (isSpaceCombat) {
            Consumer<Player> addForesightButton = (player) -> {
                if (player.hasAbility("foresight") && (player.getStrategicCC() > 0 || game.isTwilightsFallMode())) {
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + "retreat_" + pos + "_foresight",
                            "Foresight",
                            FactionEmojis.Naalu));
                }
            };
            if (!game.isFowMode()) {
                addForesightButton.accept(p2);
            }
            addForesightButton.accept(p1);

            Consumer<Player> addRalnelCommanderButton = (player) -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "ralnelcommander")
                        && !ButtonHelperModifyUnits.getRetreatSystemButtons(
                                        player, game, game.getActiveSystem(), false, false)
                                .isEmpty()) {
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + "ralnelCommander_" + pos,
                            "Retreat With Ralnel Commander",
                            FactionEmojis.Ralnel));
                }
            };
            if (!game.isFowMode()) {
                addRalnelCommanderButton.accept(p2);
            }
            addRalnelCommanderButton.accept(p1);

            Consumer<Player> addGheminaButton = (player) -> {
                if (player.hasReadyBreakthrough("gheminabt")) {
                    buttons.add(Buttons.red(
                            "retreat_" + pos + "_gheminabt",
                            "Retreat With Ghemina Breakthrough",
                            FactionEmojis.ghemina));
                }
            };
            if (!game.isFowMode()) {
                addGheminaButton.accept(p2);
            }
            addGheminaButton.accept(p1);
        }

        boolean gheminaCommanderApplicable = false;
        if (tile.getPlanetUnitHolders().isEmpty()) {
            gheminaCommanderApplicable = true;
        } else {
            for (Player p3 : game.getRealPlayers()) {
                if (CheckUnitContainmentService.getTilesContainingPlayersUnits(
                                game, p3, Units.UnitType.Pds, Units.UnitType.Spacedock)
                        .contains(tile)) {
                    gheminaCommanderApplicable = true;
                    break;
                }
            }
        }
        if (isSpaceCombat
                && game.playerHasLeaderUnlockedOrAlliance(p2, "gheminacommander")
                && gheminaCommanderApplicable
                && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.red(
                    factionChecker + "declareUse_Ghemina Commander", "Use Ghemina Commanders", FactionEmojis.ghemina));
        }
        if (isSpaceCombat
                && game.playerHasLeaderUnlockedOrAlliance(p1, "gheminacommander")
                && gheminaCommanderApplicable) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.red(
                    factionChecker + "declareUse_Ghemina Commander", "Use Ghemina Commanders", FactionEmojis.ghemina));
        }
        if (p1.hasLeaderUnlocked("keleresherokuuasi")
                && isSpaceCombat
                && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p1, game)) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeKeleresAHero", "Purge Keleres (Argent) Hero", FactionEmojis.Keleres));
        }
        if (p2.hasLeaderUnlocked("keleresherokuuasi")
                && !game.isFowMode()
                && isSpaceCombat
                && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p2, game)) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeKeleresAHero", "Purge Keleres (Argent) Hero", FactionEmojis.Keleres));
        }

        if (p1.hasLeaderUnlocked("dihmohnhero") && isSpaceCombat) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "purgeDihmohnHero", "Purge Dih-Mohn Hero", FactionEmojis.dihmohn));
        }
        if (p2.hasLeaderUnlocked("dihmohnhero") && !game.isFowMode() && isSpaceCombat) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(
                    Buttons.gray(factionChecker + "purgeDihmohnHero", "Purge Dih-Mohn Hero", FactionEmojis.dihmohn));
        }

        if (p1.hasLeaderUnlocked("kortalihero")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeKortaliHero_" + p2.getFaction(),
                    "Purge Kortali Hero",
                    FactionEmojis.kortali));
        }
        if (p2.hasLeaderUnlocked("kortalihero") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeKortaliHero_" + p1.getFaction(),
                    "Purge Kortali Hero",
                    FactionEmojis.kortali));
        }

        if ((p1.hasLeaderUnlocked("redcreusshero") || p1.hasLeaderUnlocked("crimsonhero")) && isSpaceCombat) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeRedCreussHero_" + tile.getPosition(),
                    "Purge Rebellion Hero",
                    FactionEmojis.Crimson));
        }
        if ((p2.hasLeaderUnlocked("redcreusshero") || p2.hasLeaderUnlocked("crimsonhero"))
                && !game.isFowMode()
                && isSpaceCombat) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeRedCreussHero_" + tile.getPosition(),
                    "Purge Rebellion Hero",
                    FactionEmojis.Crimson));
        }
        if (p1.hasLeaderUnlocked("bastionhero")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeBastionHero_" + tile.getPosition(),
                    "Purge Bastion Hero",
                    FactionEmojis.Bastion));
        }
        if (p2.hasLeaderUnlocked("bastionhero") && !game.isFowMode()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "purgeBastionHero_" + tile.getPosition(),
                    "Purge Bastion Hero",
                    FactionEmojis.Bastion));
        }

        if (game.isLiberationC4Mode()) {
            if ("c41".equalsIgnoreCase(tile.getTileID())) {
                Player sol = game.getPlayerFromColorOrFaction("sol");
                Player xxcha = game.getPlayerFromColorOrFaction("xxcha");
                if (sol == p1 || sol == p2 || xxcha == p1 || xxcha == p2) {
                    if (xxcha.hasLeaderUnlocked("orlandohero")) {
                        buttons.add(Buttons.gray(
                                xxcha.factionButtonChecker() + "purgeOrlandoHero_" + tile.getPosition(),
                                "Purge Orlando Hero",
                                FactionEmojis.Xxcha));
                    }
                }
            }
            if (!game.getCustomPublicVP().containsKey("Control Ordinian")) {
                Player nekro = game.getPlayerFromColorOrFaction("nekro");
                if (nekro == p1 || nekro == p2) {
                    String po_name = "Liberate Ordinian";
                    int value = game.getRevealedPublicObjectives().get(po_name);
                    if (game.getRevealedPublicObjectives().get(po_name) != null) {
                        buttons.add(Buttons.gray(
                                Constants.PO_SCORING + value, "Score " + po_name + " (Win Against Nekro)"));
                    }
                }
            }
        }

        if (ButtonHelper.getTilesOfUnitsWithBombard(p1, game).contains(tile)
                || ButtonHelper.getTilesOfUnitsWithBombard(p2, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Buttons.gray(
                        "bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll BOMBARDMENT"));
            } else {
                buttons.add(Buttons.gray(
                        "combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll BOMBARDMENT"));
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "cheirancommander")
                && isGroundCombat
                && p1 != game.getActivePlayer()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "cheiranCommanderBlock_hm",
                    "Block with Cheiran Commander",
                    FactionEmojis.cheiran));
        }
        if (!game.isFowMode()
                && game.playerHasLeaderUnlockedOrAlliance(p2, "cheirancommander")
                && isGroundCombat
                && p2 != game.getActivePlayer()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "cheiranCommanderBlock_hm",
                    "Block with Cheiran Commander",
                    FactionEmojis.cheiran));
        }

        if (p1.hasTechReady("absol_x89") && isGroundCombat && p1 != game.getActivePlayer()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.green(
                    factionChecker + "exhaustTech_absol_x89", "X-89 Bacterial Weapon", TechEmojis.BioticTech));
        }
        if (!game.isFowMode() && p2.hasTechReady("absol_x89") && isGroundCombat && p2 != game.getActivePlayer()) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.green(
                    factionChecker + "exhaustTech_absol_x89", "X-89 Bacterial Weapon", TechEmojis.BioticTech));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "kortalicommander")) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "kortaliCommanderBlock_hm",
                    "Block with Kortali Commander",
                    FactionEmojis.kortali));
        }
        if (!game.isFowMode() && game.playerHasLeaderUnlockedOrAlliance(p2, "kortalicommander")) {
            String factionChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Buttons.gray(
                    factionChecker + "kortaliCommanderBlock_hm",
                    "Block with Kortali Commander",
                    FactionEmojis.kortali));
        }
        for (UnitHolder unitH : tile.getUnitHolders().values()) {
            String nameOfHolder = "Space";
            if (unitH instanceof Planet) {
                nameOfHolder = Helper.getPlanetRepresentation(unitH.getName(), game);
                for (Player p : List.of(p1, p2)) {
                    Player otherP = p1;
                    if (p == p1) {
                        otherP = p2;
                    }
                    if (game.isFowMode() && p == p2) {
                        continue;
                    }
                    // Sol Commander
                    if (p.getPlanetsAllianceMode().contains(unitH.getName())
                            && game.playerHasLeaderUnlockedOrAlliance(p, "solcommander")
                            && isGroundCombat) {
                        String id = p.factionButtonChecker() + "utilizeSolCommander_" + unitH.getName();
                        String label = "Use Sol Commander on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.Sol));
                    }
                    if (p.hasUnit("tk-genesiscorps")
                            && isGroundCombat
                            && unitH.getUnitCount(UnitType.Infantry, p) > 0) {
                        String id = p.factionButtonChecker() + "utilizeSolCommander_" + unitH.getName();
                        String label = "Use Genesis Corps on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.Sol));
                    }
                    if (p != game.getActivePlayer()
                            && p.hasUnlockedBreakthrough("mykomentoribt")
                            && p.getNombox().getUnitCount(UnitType.Infantry, p) > 0
                            && isGroundCombat) {
                        String id = p.factionButtonChecker() + "utilizeMykoBT_" + unitH.getName();
                        String label = "Roll Myko Breakthrough on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.mykomentori));
                    }
                    if (p.hasUnit("blacktf_mech")
                            && isGroundCombat
                            && unitH.getUnitCount(UnitType.Mech, p) > 0
                            && p.getNombox().getUnitCount(UnitType.Infantry, p) > 0) {
                        buttons.add(Buttons.gray(
                                p.factionButtonChecker() + "blackTFMechReroll_" + tile.getPosition() + "_"
                                        + unitH.getName(),
                                "Reroll 1 Mech on " + nameOfHolder,
                                FactionEmojis.blacktf));
                    }

                    // atokera
                    if (p.hasUnit("atokera_mech")
                            && isGroundCombat
                            && p.getReadiedPlanets().contains(unitH.getName())
                            && unitH.getUnitCount(UnitType.Mech, p) > 0) {
                        String id = p.factionButtonChecker() + "utilizeAtokeraMech_" + unitH.getName();
                        String label = "Use Atokera Mech Ability on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.atokera));
                    }
                    if (p != game.getActivePlayer()
                            && p.hasLeaderUnlocked("pharadnhero")
                            && isGroundCombat
                            && (unitH.getUnitCount(UnitType.Pds, p) > 0
                                    || unitH.getUnitCount(UnitType.Spacedock, p) > 0)) {
                        String id = p.factionButtonChecker() + "utilizePharadnHero_" + unitH.getName();
                        String label = "Use Pharadn Hero on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.pharadn));
                    }
                    if (p != game.getActivePlayer()
                            && game.playerHasLeaderUnlockedOrAlliance(p, "pharadncommander")
                            && isGroundCombat
                            && unitH.getUnitCount(Units.UnitType.Infantry, otherP.getColor()) > 0) {
                        String id = p.factionButtonChecker() + "utilizePharadnCommander_" + unitH.getName();
                        String label = "Use Pharadn Commander on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.pharadn));
                    }
                    // Yin Indoctrinate
                    if (p.hasAbility("indoctrination")
                            && isGroundCombat
                            && unitH.getUnitCount(Units.UnitType.Infantry, otherP.getColor()) > 0) {
                        String id = p.factionButtonChecker() + "initialIndoctrination_" + unitH.getName();
                        String label = "Indoctrinate on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.Yin));
                    }

                    // Magen
                    if ((p.hasTech("md") || p.hasTech("md_c1"))
                            && isGroundCombat
                            && (unitH.getUnitCount(Units.UnitType.Spacedock, p.getColor()) > 0
                                    || unitH.getUnitCount(Units.UnitType.Pds, p.getColor()) > 0)) {
                        String id = p.factionButtonChecker() + "magenHit_" + unitH.getName();
                        String label = "Use Magen Defense Grid on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, TechEmojis.WarfareTech));
                    }
                    if (p.hasTech("tf-stealthcorps") && game.getActivePlayer() == p && isGroundCombat) {
                        String id = p.factionButtonChecker() + "stealthcorpsHit_" + unitH.getName();
                        String label = "Use Stealth Corps on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.tnelis));
                    }
                    if (p.hasUnit("tk-blacktrenchbulwark")
                            && unitH.getUnitCount(Units.UnitType.Pds, p.getColor()) > 0) {
                        String id = p.factionButtonChecker() + "magenHit_" + unitH.getName();
                        String label = "Use Black Trench Bulwark on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, UnitEmojis.pds));
                    }
                    if (p.hasAbility("ruthless")
                            && isGroundCombat
                            && otherP.getExhaustedPlanets().contains(unitH.getName())) {
                        String id = p.factionButtonChecker() + "ruthlessHit_" + unitH.getName();
                        String label = "Use Ruthless on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.kortali));
                    }
                    // Letnev Mech
                    if (p.hasUnit("letnev_mech")
                            && !ButtonHelper.isLawInPlay(game, "articles_war")
                            && isGroundCombat
                            && unitH.getUnitCount(Units.UnitType.Infantry, p.getColor()) > 0
                            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p, "mech") < 4) {
                        String id = p.factionButtonChecker() + "letnevMechRes_" + unitH.getName() + "_mech";
                        String label = "Deploy Dunlain Reaper on " + nameOfHolder;
                        buttons.add(Buttons.gray(id, label, FactionEmojis.Letnev));
                    }
                    if (isGroundCombat) {
                        BastionTechService.addProximaCombatButton(game, p1, p2, tile, unitH, buttons);
                    }
                }
                // Assimilate
                if (p1.hasAbility("assimilate")
                        && isGroundCombat
                        && (unitH.getUnitCount(Units.UnitType.Spacedock, p2.getColor()) > 0
                                || unitH.getUnitCount(Units.UnitType.Pds, p2.getColor()) > 0)) {
                    String id = p1.factionButtonChecker() + "assimilate_" + unitH.getName();
                    String label = "Assimilate Structures on " + nameOfHolder;
                    buttons.add(Buttons.gray(id, label, FactionEmojis.L1Z1X));
                }
                // vaden mechs are asymmetricish
                if (p1.hasUnit("vaden_mech")
                        && unitH.getUnitCount(Units.UnitType.Mech, p1) > 0
                        && isGroundCombat
                        && p1.getDebtTokenCount(p2.getColor(), Constants.VADEN_DEBT_POOL) > 0) {
                    String id = p1.factionButtonChecker() + "resolveVadenMech_" + unitH.getName() + "_" + p2.getColor();
                    String label = "Vaden Mech Ability on " + nameOfHolder;
                    buttons.add(Buttons.gray(id, label, FactionEmojis.vaden));
                }
                if (p2.hasUnit("vaden_mech")
                        && unitH.getUnitCount(Units.UnitType.Mech, p2) > 0
                        && isGroundCombat
                        && p2.getDebtTokenCount(p1.getColor(), Constants.VADEN_DEBT_POOL) > 0) {
                    String id = p2.factionButtonChecker() + "resolveVadenMech_" + unitH.getName() + "_" + p1.getColor();
                    String label = "Vaden Mech Ability on " + nameOfHolder;
                    buttons.add(Buttons.gray(id, label, FactionEmojis.vaden));
                }
            }
            if ("space".equalsIgnoreCase(nameOfHolder) && isSpaceCombat) {
                buttons.add(Buttons.gray("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
                if (p1.isDummy() || p1.isNpc()) {
                    buttons.add(Buttons.gray(
                                    p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(),
                                    "Roll Space Combat For Dummy")
                            .withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                }
                if (p2.isDummy() || p2.isNpc()) {
                    buttons.add(Buttons.gray(
                                    p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(),
                                    "Roll Space Combat For Dummy")
                            .withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
            } else {
                if (!isSpaceCombat && !"space".equalsIgnoreCase(nameOfHolder)) {
                    buttons.add(Buttons.gray(
                            "combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat For " + nameOfHolder));
                    Player nonActive = p1;
                    if (p1 == game.getActivePlayer()) {
                        nonActive = p2;
                    }
                    if (p1.isDummy() || p1.isNpc()) {
                        buttons.add(Buttons.gray(
                                        p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(),
                                        "Roll Ground Combat For " + nameOfHolder + " For Dummy")
                                .withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                    }
                    if (p2.isDummy() || p2.isNpc()) {
                        buttons.add(Buttons.gray(
                                        p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(),
                                        "Roll Ground Combat For " + nameOfHolder + " For Dummy")
                                .withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                    }
                    Request spaceCannonRequest = new Request(nonActive, game, null, tile, unitH.getName());
                    if (CombatV2UnitService.checkIfUnitsOfType(spaceCannonRequest, CombatRollType.SpaceCannonDefence)) {
                        Player target = nonActive == p1 ? p2 : p1;
                        if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))
                                && NetrunnersUnitsHandler.empBlocksSpaceCannonAgainst(
                                        target, tile, CombatRollType.SpaceCannonDefence)) {
                            continue;
                        }
                        buttons.add(Buttons.gray(
                                "combatRoll_" + tile.getPosition() + "_" + unitH.getName() + "_spacecannondefence",
                                "Roll SPACE CANNON Defence for " + nameOfHolder));
                        if (game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("control_network"))) {
                            buttons.addAll(NetrunnersAbilitiesHandler.getControlNetworkSpaceCannonButtons(
                                    game, nonActive, tile, CombatRollType.SpaceCannonDefence, unitH.getName()));
                        }
                    }
                }
            }
        }
        return buttons;
    }

    private static void checkAndAddDihmonBreakthroughButton(
            Player player, boolean isSpaceCombat, List<Button> buttons, Tile tile) {
        if (isSpaceCombat && player.hasTechReady("dihmohnbt")) {
            if (player.hasReadyBreakthrough("dihmohnbt")) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "exhaustBT_dihmohnbt_" + tile.getPosition(),
                        "Place Frontier Token (Upon Destroy)",
                        FactionEmojis.dihmohn));
            } else {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "readyBT_dihmohnbt_" + tile.getPosition(),
                        "Produce 1 Non-Fighter Ship (Upon Destroy)",
                        FactionEmojis.dihmohn));
            }
        }
    }

    private static void checkAndAddFractalPlatingButton(
            Game game, Player player, boolean isSpaceCombat, Tile tile, List<Button> buttons) {
        if (!isSpaceCombat
                || !player.hasTechReady("dsmortr")
                || !FoWHelper.playerHasShipsInAdjacentSystems(player, tile, game)) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "exhaustTech_dsmortr",
                "Exhaust Fractal Plating (Upon Destroy)",
                FactionEmojis.mortheus));
    }

    private static void checkAndAddSubatomicButton(
            Game game, Player player, boolean isSpaceCombat, Tile tile, List<Button> buttons) {
        if (!isSpaceCombat || (!player.hasTech("nekroc4y") && !player.hasTech("subatomic"))) {
            return;
        }
        Tile homeSystemTile = player.getHomeSystemTile();
        if (homeSystemTile == null || tile == homeSystemTile) {
            return;
        }
        if (player.hasUnit("ghoti_flagship")
                || CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)
                        .contains(homeSystemTile)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "useNekroNullRef",
                    "Use Subatomic Splicer (Upon Each Destroy)",
                    FactionEmojis.Crimson));
        }
    }

    private static void checkAndAddIncomprehensibleFormButton(
            Game game, Player p1, Player p2, boolean isSpaceCombat, Tile tile, List<Button> buttons) {
        if (!isSpaceCombat
                || tile == null
                || (!p1.hasAbility("incomprehensible_form") && !p2.hasAbility("incomprehensible_form"))
                || !DreamButtonHandler.tileContainsNexusToken(game, tile, true)) {
            return;
        }
        buttons.addAll(DreamButtonHandler.getIncomprehensibleFormButtons(game, p1, p2, tile));
    }
}
