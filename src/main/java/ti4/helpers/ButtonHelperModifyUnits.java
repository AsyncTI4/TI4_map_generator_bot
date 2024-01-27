package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.combat.StartCombat;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class ButtonHelperModifyUnits {

    public static int getNumberOfSustainableUnits(Player player, Game activeGame, UnitHolder unitHolder){
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int sustains = 0;
        Player mentak = Helper.getPlayerFromUnit(activeGame,"mentak_mech");
        if(mentak != null && mentak != player && unitHolder.getUnitCount(UnitType.Mech, mentak.getColor()) > 0){
            return 0;
        }
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null) continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
            int damagedUnits = 0;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            int totalUnits = unitEntry.getValue() - damagedUnits;
            if (unitModel.getSustainDamage()) {
                sustains = sustains+totalUnits;
            }
        }
        return sustains;
    }

    public static void autoAssignAntiFighterBarrageHits(Player player, Game activeGame, String pos, int hits, ButtonInteractionEvent event){
        Tile tile = activeGame.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        String msg = ButtonHelper.getIdent(player)+" assigned hits in the following way:\n";
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, activeGame, unitHolder);
        Player cabal = Helper.getPlayerFromAbility(activeGame, "devour");
        if(hits > 0 && unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("fighter") && min > 0) {
                    msg = msg + "> Destroyed "+min+" "+Emojis.fighter+"\n";
                    hits = hits - min;
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);

                    if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                    && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                    }
                }
            }
        }
        Player argent = Helper.getPlayerFromAbility(activeGame, "raid_formation");
        if(hits > 0 && argent != null && FoWHelper.playerHasShipsInSystem(argent, tile) && argent != player && numSustains > 0){
           for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if (unitModel.getSustainDamage() && min > 0) {
                    msg = msg + "> Made "+min+" "+unitModel.getUnitEmoji()+" sustained\n";
                    hits = hits - min;
                    tile.addUnitDamage("space", unitKey, min);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        event.getMessage().delete().queue();
    }
    public static void autoAssignGroundCombatHits(Player player, Game activeGame, String planet, int hits, ButtonInteractionEvent event){
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        String msg = ButtonHelper.getIdent(player)+" assigned hits in the following way:\n";
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, activeGame, unitHolder);
        Player cabalMechOwner = Helper.getPlayerFromUnit(activeGame, "cabal_mech");
        boolean cabalMech = cabalMechOwner != null && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0 
            && !activeGame.getLaws().containsKey("articles_war");
        Tile tile = activeGame.getTileFromPlanet(planet);
        Player cabal = Helper.getPlayerFromAbility(activeGame, "devour");
        if(numSustains > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if(player.hasTech("nes")){
                    min = Math.min(totalUnits, (hits+1)/2);
                }
                if (unitModel.getSustainDamage() && min > 0) {
                    msg = msg + "> Sustained "+min+" "+unitModel.getUnitEmoji()+"\n";
                    hits = hits - min;
                    if(player.hasTech("nes")){
                        hits = hits - min;
                    }
                    tile.addUnitDamage(planet, unitKey, min);
                    for (int x = 0; x < min; x++) {
                        ButtonHelperCommanders.resolveLetnevCommanderCheck(player, activeGame, event);
                    }
                }
            }
        }
        if(hits > 0 && player.hasUnit("naalu_flagship") && unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("fighter") && min > 0) {
                    msg = msg + "> Destroyed "+min+" "+Emojis.fighter+"\n";
                    hits = hits - min;
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);

                    if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                    && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planet)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                    }
                }
            }
        }

        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("infantry") && min > 0) {
                    msg = msg + "> Destroyed "+min+" "+Emojis.infantry+"\n";
                    hits = hits - min;
                    if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2")) && unitName.toLowerCase().contains("inf")) {
                        ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                    }
                    if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                        ButtonHelper.resolveInfantryDeath(activeGame, player, min);
                    }
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                    if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                    && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planet)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                    }
                }
            }
        }
        if(hits > 0 && (player.hasUnit("titans_pds") || player.hasUnit("titans_pds2"))&& unitHolder.getUnitCount(UnitType.Pds, player.getColor()) > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("pds") && min > 0) {
                    msg = msg + "> Destroyed "+min+" "+Emojis.pds+"\n";
                    hits = hits - min;
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                }
            }
        }

        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("mech") && min > 0) {
                    msg = msg + "> Destroyed "+min+" "+unitName+"(s)\n";
                    hits = hits - min;
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                    if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                    && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planet)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                    }
                    if(player.hasTech("sar")){
                        for (int x = 0; x < min; x++) {
                            player.setTg(player.getTg() + 1);
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                            ButtonHelperAbilities.pillageCheck(player, activeGame);
                        }
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    }
                    if ( player.hasUnit("mykomentori_mech")) {
                        for (int x = 0; x < min; x++) {
                            ButtonHelper.rollMykoMechRevival(activeGame, player);
                        }
                    }
                }
                
            }
        }


        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        event.getMessage().delete().queue();
    }


























    public static String autoAssignSpaceCombatHits(Player player, Game activeGame, Tile tile, int hits, GenericInteractionCreateEvent event, boolean justSummarizing){
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        String msg = ButtonHelper.getIdent(player)+" assigned hits in the following way:\n";
        if(justSummarizing){
            msg = ButtonHelper.getIdent(player)+" would assign hits in the following way:\n";
        }
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, activeGame, unitHolder);
        boolean noMechPowers = activeGame.getLaws().containsKey("articles_war");
        Player cabal = Helper.getPlayerFromAbility(activeGame, "devour");
        if(numSustains > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                boolean isNomadMechApplicable = false;
                if((ButtonHelper.getUnitName(unitKey.asyncID()).equalsIgnoreCase("mech") && player.hasUnit("nomad_mech") && !noMechPowers)){
                    isNomadMechApplicable = true;
                }
                if(!unitModel.getIsShip() && !isNomadMechApplicable){
                    continue;
                }
               
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if(player.hasTech("nes")){
                    min = Math.min(totalUnits, (hits+1)/2);
                }
                String stuffNotToSustain = activeGame.getFactionsThatReactedToThis("stuffNotToSustainFor"+player.getFaction());

                if(stuffNotToSustain.isEmpty()){
                    activeGame.setCurrentReacts("stuffNotToSustainFor"+player.getFaction(), "warsun");
                    stuffNotToSustain = "warsun";
                }
                if (unitModel.getSustainDamage() && min > 0 && (!stuffNotToSustain.contains(unitName.toLowerCase()) || (unitName.equalsIgnoreCase("dreadnought") && player.hasUpgradedUnit("dn2")))) {
                    hits = hits - min;
                    if(player.hasTech("nes")){
                        hits = hits - min;
                    }
                    if(!justSummarizing){
                        msg = msg + "> Sustained "+min+" "+unitModel.getUnitEmoji()+"\n";
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, activeGame, event);
                        }
                    }else{
                        msg = msg + "> Would sustain "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("fighter") && min > 0) {
                    
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("destroyer") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }

        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("cruiser") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        //catch any sustains not already caught
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                if(!unitModel.getIsShip()){
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if(player.hasTech("nes")){
                    min = Math.min(totalUnits, (hits+1)/2);
                }
                String stuffNotToSustain = activeGame.getFactionsThatReactedToThis("stuffNotToSustainFor"+player.getFaction());

                if(stuffNotToSustain.isEmpty()){
                    activeGame.setCurrentReacts("stuffNotToSustainFor"+player.getFaction(), "warsun");
                    stuffNotToSustain = "warsun";
                }
                if (unitModel.getSustainDamage() && min > 0 && !(!stuffNotToSustain.contains(unitName.toLowerCase()) || (unitName.equalsIgnoreCase("dreadnought") && player.hasUpgradedUnit("dn2")))) {
                    msg = msg + "> Sustained "+min+" "+unitModel.getUnitEmoji()+"\n";
                    hits = hits - min;
                    if(player.hasTech("nes")){
                        hits = hits - min;
                    }
                    if(!justSummarizing){
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, activeGame, event);
                        }
                    }
                }
            }
        }
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("dreadnought") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        if(hits > 0 && player.hasUnit("naaz_mech_space") && !noMechPowers){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("mech") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                        if(player.hasTech("sar")){
                            for (int x = 0; x < min; x++) {
                                player.setTg(player.getTg() + 1);
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                    + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                ButtonHelperAbilities.pillageCheck(player, activeGame);
                            }
                            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                        }
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }

                }
            }
        }
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("carrier") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("flagship") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }
        if(hits > 0){
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("warsun") && min > 0) {
                    hits = hits - min;
                    if(!justSummarizing){
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, min, unitName, event);
                        }
                        msg = msg + "> Destroyed "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }else{
                        msg = msg + "> Would destroy "+min+" "+unitModel.getUnitEmoji()+"\n";
                    }
                }
            }
        }


        if(!justSummarizing && event instanceof ButtonInteractionEvent bevent){
            bevent.getMessage().delete().queue();
        }
        return msg;
        
    }


     public static List<Button> getRemoveThisTypeOfUnitButton(Player player, Game activeGame, String unit) {
        List<Button> buttons = new ArrayList<>();
        UnitType type = Mapper.getUnitKey(AliasHandler.resolveUnit(unit),player.getColorID()).getUnitType();
        for(Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, type)){
            for(UnitHolder uH : tile.getUnitHolders().values()){
                if(uH.getUnitCount(type, player.getColor()) > 0){
                        buttons.add(Button.danger("removeThisTypeOfUnit_"+type.humanReadableName()+"_"+tile.getPosition()+"_"+uH.getName(),"Remove 1 "+type.humanReadableName()+" from tile " + 
                        tile.getPosition()+ " in location: "+uH.getName()));
                }
            }
        }
        buttons.add(Button.secondary("deleteButtons", "Done Resolving"));
        return buttons;
     }

     public static void removeThisTypeOfUnit(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String unit = buttonID.split("_")[1].toLowerCase().replace(" ", "").replace("'","");
        String tilePos = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(tilePos);
        String unitH = buttonID.split("_")[3];
        UnitHolder uH = tile.getUnitHolders().get(unitH);

        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 "+ unit+ " "+unitH.replace("space",""), activeGame);
        if(uH.getUnitCount(Mapper.getUnitKey(AliasHandler.resolveUnit(unit),player.getColorID()).getUnitType(), player.getColor()) < 1){
            ButtonHelper.deleteTheOneButton(event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()+ " removed 1 "+unit+ " from tile "+tile.getRepresentationForButtons(activeGame, player) +" location: "+unitH);
     }

    

    public static void infiltratePlanet(Player player, Game activeGame, UnitHolder uH, ButtonInteractionEvent event) {
        int sdAmount = 0;
        int pdsAmount = 0;
        for (Player p2 : activeGame.getRealPlayers()) {
            if(p2 == player){
                continue;
            }
            sdAmount = uH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) + sdAmount + uH.getUnitCount(UnitType.Spacedock, p2.getColor());
            if(uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0){
                new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " sd " + uH.getName(), activeGame);
                new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " csd " + uH.getName(), activeGame);
            }
            pdsAmount = uH.getUnitCount(UnitType.Pds, p2.getColor()) + pdsAmount;
            if(uH.getUnitCount(UnitType.Pds, p2.getColor()) > 0){
                new RemoveUnits().unitParsing(event, p2.getColor(), activeGame.getTileFromPlanet(uH.getName()), pdsAmount + " pds " + uH.getName(), activeGame);
            }
        }
        if (pdsAmount > 0) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), pdsAmount + " pds " + uH.getName(), activeGame);
        }
        if (player.hasUnit("cabal_spacedock") && sdAmount > 0) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " sd " + uH.getName(), activeGame);
        } else {
            if (sdAmount > 0) {
                new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(uH.getName()), sdAmount + " sd " + uH.getName(), activeGame);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdentOrColor(player, activeGame) + " replaced " + pdsAmount + " pds and " + sdAmount + " space docks on "
            + Helper.getPlanetRepresentation(uH.getName(), activeGame) + " with their own units");

    }

    public static List<Button> getRetreatSystemButtons(Player player, Game activeGame, String pos1, boolean skilled) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        String skilledS = "";
        if (skilled) {
            skilledS = "_skilled";
        }
        HashSet<String> adjTiles = new HashSet();
        adjTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, pos1, player, false));
        if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")){
            Tile hs = FoWHelper.getPlayerHS(activeGame, player);
            if(hs != null){
                adjTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, hs.getPosition(), player, false));
            }
        }
        List<String> checked = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            if(checked.contains(pos2)){
                continue;
            }
            checked.add(pos2);
            Tile tile2 = activeGame.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                if (!FoWHelper.otherPlayersHaveUnitsInSystem(player, tile2, activeGame) || skilled) {
                    if (FoWHelper.playerIsInSystem(activeGame, tile2, player) || player.hasTech("det") || skilled) {
                        buttons.add(Button.secondary(finChecker + "retreatUnitsFrom_" + pos1 + "_" + pos2 + skilledS, "Retreat to " + tile2.getRepresentationForButtons(activeGame, player)));
                    }
                }
            }

        }
        return buttons;
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Game activeGame, ButtonInteractionEvent event, String pos1, String pos2) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = activeGame.getTileByPosition(pos1);
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet planet) {
                int limit = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    Button validTile2 = Button.success(finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "infantry_" + representation,
                        "Retreat " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    buttons.add(validTile2);
                }
                limit = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    Button validTile2 = Button.primary(finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "mech_" + representation,
                        "Retreat " + x + " Mech(s) on " + Helper.getPlanetRepresentation(representation.toLowerCase(), activeGame)).withEmoji(Emoji.fromFormatted(Emojis.mech));
                    buttons.add(validTile2);
                }

            }
        }
        Button concludeMove = Button.secondary(finChecker + "deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "empyrean", event);
        }
        return buttons;
    }

    public static void finishLanding(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        if (!event.getMessage().getContentRaw().contains("Moved all units to the space area.")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        }

        String message = "Landed troops. Use buttons to decide if you want to build or finish the activation";
        ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, activeGame, event);
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(unitHolder.getName())) {
                continue;
            }
            List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, unitHolder.getName());
            if (players.size() > 1 && !player.getAllianceMembers().contains(players.get(0).getFaction()) && !player.getAllianceMembers().contains(players.get(1).getFaction())) {
                Player player2 = players.get(0);
                if (player2 == player) {
                    player2 = players.get(1);
                }
                String threadName = StartCombat.combatThreadName(activeGame, player, player2, tile);
                if (!activeGame.isFoWMode()) {
                    StartCombat.findOrCreateCombatThread(activeGame, activeGame.getActionsChannel(), player, player2, threadName, tile, event, "ground");
                } else {
                    StartCombat.findOrCreateCombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile, event, "ground");
                    StartCombat.findOrCreateCombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile, event, "ground");
                    for (Player player3 : activeGame.getRealPlayers()) {
                        if (player3 == player2 || player3 == player) {
                            continue;
                        }
                        if (!tile.getRepresentationForButtons(activeGame, player3).contains("(")) {
                            continue;
                        }
                        StartCombat.findOrCreateCombatThread(activeGame, player3.getPrivateChannel(), player3, player3, threadName, tile, event, "ground");
                    }
                }
                if (player2.ownsUnit("keleres_mech") && unitHolder.getUnitCount(UnitType.Mech, player2.getColor()) > 0) {
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player,  "inf");
                    Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " you must pay influence due to Keleres mech(s)");
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), "Click the names of the planets you wish to exhaust", buttons);
                }
            }
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                List<Button> b2s = new ArrayList<>();
                b2s.add(Button.success("returnFFToSpace_" + tile.getPosition(), "Return Fighters to Space"));
                b2s.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true)
                    + " you can use this button to return naalu fighters to space after combat concludes. This only needs to be done once. Reminder you cant take over a planet with only fighters.", b2s);
            }
        }
        List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "cheiran", event);
        event.getMessage().delete().queue();
    }

    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("retreatGroundUnits_", "").replace("'", "");
        String pos1 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos1 + "_", "");
        String pos2 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos2 + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitType;
        String planet = "";
        if (rest.contains("_")) {
            unitType = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase();
        } else {
            unitType = rest;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitType), player.getColor());
        if (buttonLabel.toLowerCase().contains("damaged")) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos2), amount + " " + unitType, activeGame);
            activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, amount);
        } else {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos2), amount + " " + unitType, activeGame);
        }

        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos1), amount, planet, unitKey, player.getColor(), false, activeGame);

        List<Button> systemButtons = getRetreatingGroundTroopsButtons(player, activeGame, event, pos1, pos2);
        String retreatMessage = ident + " Retreated " + amount + " " + unitType + " on " + planet + " to " + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), retreatMessage);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        Tile tile2 = activeGame.getTileByPosition(pos2);
        tile2 = MoveUnits.flipMallice(event, tile2, activeGame);
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "kollecccommander") && !buttonID.contains("skilled") && !AddCC.hasCC(event, player.getColor(), tile1)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " did not place a cc in the retreat system due to kollecc commander");
        } else {
            AddCC.addCC(event, player.getColor(), tile2, true);
        }

        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet) continue;
            //retreat capacity units first to avoid false cap flags
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                if(unitModel.getCapacityValue() < 1){
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, activeGame);
                if (damagedUnits > 0) {
                    activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
            //this will catch all the capacity units left behind in the previous iteration
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;
                if(unitModel.getCapacityValue() >0){
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false, activeGame);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, activeGame);
                if (damagedUnits > 0) {
                    activeGame.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
        }
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity, String finsFactionCheckerPrefix) {
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong.replace("2",""));
        String spaceOrPlanet;
        
        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit, activeGame);
                successMessage = "Placed a space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), "sd " + planetName, activeGame);
                successMessage = "Placed a space dock on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                if (player.getLeaderIDs().contains("cabalcommander") && !player.hasLeaderUnlocked("cabalcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "cabal", event);
                }
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    activeGame);
                successMessage = "Placed a " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }

            if(player.hasAbility("industrious") && !FoWHelper.otherPlayersHaveShipsInSystem(player, activeGame.getTile(AliasHandler.resolveTile(planetName)), activeGame)){
                Button replace = Button.success("FFCC_" + player.getFaction()+"_rohdhnaIndustrious_" 
                    + activeGame.getTile(AliasHandler.resolveTile(planetName)).getPosition() + "_" + unit + " " + planetName, "Replace SD with Warsun");
                    
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), playerRep + "Industrious: You may spend 6 resources to replace SD with a Warsun.", replace);
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Emojis.pds + " on "
                + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
            Tile tile;
            String producedOrPlaced = "Produced";
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || (unitLong.contains("gf") && unitLong.length() > 2)) {
                if ((unitLong.contains("gf") && unitLong.length() > 2)) {
                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        String num = unitLong.substring(0, 1);
                        String producedInput = unit.replace(num,"")+"_"+tile.getPosition()+"_"+spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for(int x = 0;x < nu; x++){
                            player.produceUnit(producedInput);
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), num+" gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " "+num+" " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        String num = unitLong.substring(0, 1);
                        String producedInput = unit.replace(num,"")+"_"+tile.getPosition()+"_"+spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for(int x = 0;x < nu; x++){
                            player.produceUnit(producedInput);
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, num+" gf",
                            activeGame);
                        successMessage = producedOrPlaced + " "+num+" " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        String producedInput = unit.replace("2","")+"_"+tile.getPosition()+"_"+spaceOrPlanet;
                        player.produceUnit(producedInput);
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        String producedInput = unit.replace("2","")+"_"+tile.getPosition()+"_"+spaceOrPlanet;
                        player.produceUnit(producedInput);
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                spaceOrPlanet = "space";
                tile = activeGame.getTileByPosition(planetName);
                String producedInput = unit.replace("2","")+"_"+tile.getPosition()+"_"+spaceOrPlanet;
                player.produceUnit(producedInput);
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        "2 ff", activeGame);
                    successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                     player.produceUnit(producedInput);
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        "2 destroyer", activeGame);
                    successMessage = "Produced 2 " + Emojis.destroyer + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                        player.produceUnit(producedInput);
                        
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName),
                        unit, activeGame);
                    successMessage = "Produced a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile "
                        + AliasHandler.resolveTile(planetName) + ".";
                        
                }

            }
        }
        if (("sd".equalsIgnoreCase(unit) || "pds".equalsIgnoreCase(unitLong)) && event.getMessage().getContentRaw().contains("for construction")) {

            if (activeGame.isFoWMode() || !"action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
            } else {
                ButtonHelper.sendMessageToRightStratThread(player, activeGame, playerRep + " " + successMessage, "construction");
            }
            if (player.hasLeader("mahactagent") || player.hasExternalAccessToLeader("mahactagent")) {
                String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                Button placeCCInSystem = Button.success(
                    finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                    "Place A CC From Reinforcements In The System.");
                Button placeConstructionCCInSystem = Button.secondary(
                    finsFactionCheckerPrefix + "placeHolderOfConInSystem_" + planetName,

                    "Place A CC From The Construction Holder's Reinforcements by using Mahact Agent");
                Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                    "Don't Place A CC In The System.");
                List<Button> buttons = List.of(placeCCInSystem, placeConstructionCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            } else {
                if (!player.getSCs().contains(Integer.parseInt("4")) && "action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                    String color = player.getColor();
                    String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                    Tile tile = activeGame.getTile(tileID);
                    if (tile == null) {
                        tile = activeGame.getTileByPosition(tileID);
                    }
                    String msg = playerRep + " Placed A CC From Reinforcements In The "
                                + Helper.getPlanetRepresentation(planetName, activeGame) + " system";
                    if(!activeGame.playerHasLeaderUnlockedOrAlliance(player, "rohdhnacommander")){
                        if (Mapper.isValidColor(color)) {
                            AddCC.addCC(event, color, tile);
                        }
                    }else{
                        msg = playerRep +" has Rohdhna Commander and is thus doing the Primary which does not place a CC";
                    }

                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(),msg);
                    } else {
                        ButtonHelper.sendMessageToRightStratThread(player, activeGame, msg, "construction");
                    }
                }
            }

            event.getMessage().delete().queue();

        } else {
            String editedMessage = event.getMessage().getContentRaw();
            if (editedMessage.contains("Produced")) {
                editedMessage = editedMessage + "\n " + successMessage;
            } else {
                editedMessage = playerRep + " " + successMessage;
            }

            if (editedMessage.contains("place 2 infantry")) {
                successMessage = "Placed 2 " + Emojis.infantry + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, activeGame)).queue();
            }

        }
        if ("sd".equalsIgnoreCase(unit)){
            Tile tile = activeGame.getTileFromPlanet(planetName);
            if(tile != null){
                AgendaHelper.ministerOfIndustryCheck(player, activeGame, tile, event);
            }
        }
        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "rohdhna", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "cheiran", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "celdauri", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "gledge", event);
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "tnelis", event);
        }
        if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "cymiae", event);
        }
        if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "kyro", event);
        }
        if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "ghemina", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }

        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }
    }

    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild + "_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);
        String producedOrPlaced = "Produced";
        if ("skipbuild".equalsIgnoreCase(skipbuild)) {
            producedOrPlaced = "Placed";
        }
        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit, activeGame);
                successMessage = "Placed a space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + " system.";
            } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), "sd " + planetName, activeGame);
                successMessage = "Placed a space dock on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    activeGame);
                successMessage = "Placed a " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            new AddUnits().unitParsing(event, player.getColor(),
                activeGame.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                activeGame);
            successMessage = "Placed a " + Emojis.pds + " on "
                + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        } else {
            Tile tile;
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit) || ((unitLong.contains("gf") && unitLong.length() > 2))) {
                if (unitLong.contains("gf") && unitLong.length() > 2) {
                    String amount = "" + unitLong.charAt(0);
                    if (!planetName.contains("space")) {
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), amount + " gf " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, amount + " gf ",
                            activeGame);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
                    } else {
                        tile = activeGame.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            activeGame);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), "2 ff", activeGame);
                    successMessage = producedOrPlaced + " 2 " + Emojis.fighter + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), "2 destroyer", activeGame);
                    successMessage = producedOrPlaced + " 2 " + Emojis.destroyer + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(planetName), unit, activeGame);
                    successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile " + AliasHandler.resolveTile(planetName) + ".";
                }

            }
            if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
            }
            if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "cymiae", event);
            }
            if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "kyro", event);
            }
            if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "ghemina", event);
            }
            if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "tnelis", event);
            }
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), playerRep + " " + successMessage);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
        if(skipbuild.contains("freelancers")){
             buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "freelancers");
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            Button winnuButton = Button.danger("exhaustAgent_ghotiagent_"+player.getFaction(), "Use Ghoti Agent").withEmoji(Emoji.fromFormatted(Emojis.ghoti));
            buttons.add(winnuButton);
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            Button winnuButton = Button.danger("exhaustAgent_mortheusagent_"+player.getFaction(), "Use Mortheus Agent").withEmoji(Emoji.fromFormatted(Emojis.mortheus));
            buttons.add(winnuButton);
        }
        Button DoneExhausting;
        if (!buttonID.contains("deleteButtons")) {
            DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
        } else {
            DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        }
        buttons.add(DoneExhausting);
        if (!"skipbuild".equalsIgnoreCase(skipbuild)) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
        }

        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if ("sd".equalsIgnoreCase(unit)){
            Tile tile = activeGame.getTileFromPlanet(planetName);
            if(tile != null){
                AgendaHelper.ministerOfIndustryCheck(player, activeGame, tile, event);
            }
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "rohdhna", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "cheiran", event);
        ButtonHelper.fullCommanderUnlockCheck(player, activeGame, "celdauri", event);
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "tnelis", event);
        }
        if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "cymiae", event);
        }
        if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "kyro", event);
        }
        if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "ghemina", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "argent", event);
        }
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "arborec", event);
        }

        event.getMessage().delete().queue();
    }

    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("spaceUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("-", "").replace("'", "");
        } else {
            unitName = rest;
        }
         if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName.replace("damaged", "");
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), amount + " " + unitName, activeGame);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            activeGame.getTileByPosition(pos).addUnitDamage("space", unitKey, amount);
            activeGame.getTileByPosition(pos).removeUnitDamage(planet, unitKey, amount);
        }
        
        activeGame.getTileByPosition(pos).removeUnit(planet, unitKey, amount);
         if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged "+unitName;
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + "Undid landing of " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void resolveAssaultCannonNDihmohnCommander(String buttonID, ButtonInteractionEvent event, Player player, Game activeGame){
        String cause = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Player opponent = null;
         String msg;
        Tile tile = activeGame.getTileByPosition(pos);
        for(Player p2: activeGame.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile) && !player.getAllianceMembers().contains(p2.getFaction())){
                opponent = p2;
            }
        }
        if(opponent == null){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No opponent found");
            return;
        }
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(opponent, activeGame, tile);
        if(cause.contains("dihmohn")){
            msg = opponent.getRepresentation(true, true)+ " "+player.getFactionEmoji() +" used Dihmohn COmmander to generate a hit against you. Please assign it with buttons";
        }else if(cause.contains("ds")){
            msg = opponent.getRepresentation(true, true)+ " "+player.getFactionEmoji() +" used Dimensional Splicer to assign a hit of their choice. They should declare the target now, and if you dont cancel it somehow, you should assign it with buttons";
        }else{
            msg = opponent.getRepresentation(true, true)+ " "+player.getFactionEmoji() +" used assault cannon to force you to destroy a non fighter ship. Please assign it with buttons";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);

    }

    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest = buttonID.replace("landUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
        } else {
            unitName = rest;
        }
         if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName.replace("damaged", "");
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), amount + " " + unitName + " " + planet, activeGame);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            activeGame.getTileByPosition(pos).addUnitDamage(planet, unitKey, amount);
            activeGame.getTileByPosition(pos).removeUnitDamage("space", unitKey, amount);
        }
         if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged "+unitName;
        }

        activeGame.getTileByPosition(pos).removeUnit("space", unitKey, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " Landed " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void offerDomnaStep2Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : tile.getUnitHolders().get("space").getUnits().keySet()) {
            if (unit.getUnitType() == UnitType.Infantry || unit.getUnitType() == UnitType.Mech) {
                continue;
            }
            String unitName = ButtonHelper.getUnitName(unit.asyncID());
            Button validTile = Button.success("domnaStepTwo_" + pos + "_" + unitName, "Move 1 " + unit.unitName());
            buttons.add(validTile);
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select unit you want to move", buttons);
        event.getMessage().delete().queue();
    }

    public static void offerDomnaStep3Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = activeGame.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, activeGame)) {
                buttons.add(Button.secondary("domnaStepThree_" + pos1 + "_" + unit + "_" + pos2, "Move " + unit + " to " + tile2.getRepresentationForButtons(activeGame, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select tile you want to move to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveDomnaStep3Buttons(ButtonInteractionEvent event, Game activeGame, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        Tile tile1 = activeGame.getTileByPosition(pos1);
        String unit = buttonID.split("_")[2];
        String pos2 = buttonID.split("_")[3];
        Tile tile2 = activeGame.getTileByPosition(pos2);
        new AddUnits().unitParsing(event, player.getColor(), tile2, unit, activeGame);
        new RemoveUnits().unitParsing(event, player.getColor(), tile1, unit, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " moved 1 " + unit + " from " + tile1.getRepresentationForButtons(activeGame, player) + " to "
            + tile2.getRepresentationForButtons(activeGame, player) + "an ability.");
        event.getMessage().delete().queue();
    }

    public static void offerCombatDroneButtons(ButtonInteractionEvent event, Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
            for (int x = 1; x < numff + 1; x++) {
                buttons.add(Button.success("combatDroneConvert_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose how many fighters you want to convert to infantry", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void offerMirvedaCommanderButtons(ButtonInteractionEvent event, Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numinf;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numinf = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
            for (int x = 1; x < numinf + 1; x++) {
                buttons.add(Button.success("resolveMirvedaCommander_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " choose how many infantry you wish to convert to fighters", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolvingMirvedaCommander(ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonID) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", activeGame);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        String msg = ident + " Turned " + numff + " infantry into fighters using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void resolvingCombatDrones(ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonID) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", activeGame);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", activeGame);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
        String msg = ident + " Turned " + numff + " fighters into infantry using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    

    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String buttonLabel) {
        String rest;
        rest = buttonID.replace("assignHits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        Player cabal = Helper.getPlayerFromAbility(activeGame, "devour");
        if (rest.contains("All")) {
            String cID = Mapper.getColorID(player.getColor());
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                UnitHolder unitHolder = entry.getValue();
                Map<UnitKey, Integer> units1 = unitHolder.getUnits();
                Map<UnitKey, Integer> units = new HashMap<>(units1);
                if (unitHolder instanceof Planet) {
                    if (!rest.contains("AllShips")) {

                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            UnitKey unitKey = unitEntry.getKey();
                            if (!unitKey.getColorID().equals(cID)) continue;

                            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                            int amount = unitEntry.getValue();
                            Player cabalMechOwner = Helper.getPlayerFromUnit(activeGame, "cabal_mech");
                            boolean cabalMech = cabalMechOwner != null && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0 
                                && unitName.toLowerCase().contains("infantry") && !activeGame.getLaws().containsKey("articles_war");

                            new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitKey, player.getColor(), false, activeGame);
                            if (cabal != null && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, unitHolder.getName())
                                && ((!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, unitEntry.getValue(), unitName, event);
                            }
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2")) && unitName.toLowerCase().contains("inf")) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
                            }
                            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                                ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                                for (int x = 0; x < amount; x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1)
                                        + "->" + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < amount; x++) {
                                    ButtonHelper.rollMykoMechRevival(activeGame, player);
                                }
                            }
                        }
                    }
                } else {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        UnitKey unitKey = unitEntry.getKey();
                        if (!unitKey.getColorID().equals(cID)) continue;
                        String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                        int amount = unitEntry.getValue();

                        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, "space", unitKey, player.getColor(), false, activeGame);
                        if (cabal != null && FoWHelper.playerHasShipsInSystem(cabal, tile)
                            && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
                        }
                    }
                }
            }
            String message2 = ident + " Removed all units";
            if (rest.contains("AllShips")) {
                message2 = ident + " Removed all units in space area";
            }
            String message = event.getMessage().getContentRaw();
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
            event.getMessage().editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

            return;
        }
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        String planetName;
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }

        new RemoveUnits().removeStuff(event, activeGame.getTileByPosition(pos), amount, planetName, unitKey, player.getColor(), buttonLabel.toLowerCase().contains("damaged"), activeGame);

        if ("".equalsIgnoreCase(planet)) {
            if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
            }
        } else {
            boolean cabalMech = cabal != null && activeGame.getTileByPosition(pos).getUnitHolders().get(planetName).getUnitCount(UnitType.Mech, cabal.getColor()) > 0 && cabal.hasUnit("cabal_mech")
                && unitName.toLowerCase().contains("infantry") && !activeGame.getLaws().containsKey("articles_war");
            if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planetName)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, activeGame, cabal, amount, unitName, event);
            }
            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                for (int x = 0; x < amount; x++) {
                    player.setTg(player.getTg() + 1);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " you gained 1tg (" + (player.getTg() - 1) + "->"
                        + player.getTg() + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain");
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                }
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            }
            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("mykomentori_mech")) {
                for (int x = 0; x < amount; x++) {
                    ButtonHelper.rollMykoMechRevival(activeGame, player);
                }
            }
            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2")) && unitName.toLowerCase().contains("inf")) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(player, activeGame);
            }
            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                ButtonHelper.resolveInfantryDeath(activeGame, player, amount);
            }
        }

        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Removed " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

    }

    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest;
        rest = buttonID.replace("repairDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.removeUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Repaired " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }

    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String rest;
        rest = buttonID.replace("assignDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = activeGame.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.addUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Sustained " + amount + " " + unitName + " from " + planetName + " in tile " + tile.getRepresentationForButtons(activeGame, player);

        if(player.hasTech("nes")){
            message2 = message2 + ". These sustains cancel 2 hits due to Non-Euclidean Shielding";
        }
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
        for (int x = 0; x < amount; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, activeGame, event);
        }
    }

}
