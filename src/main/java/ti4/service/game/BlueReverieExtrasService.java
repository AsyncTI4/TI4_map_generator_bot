package ti4.service.game;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.AbilityModel;
import ti4.model.LeaderModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.Source.ComponentSource;
import ti4.model.UnitModel;

@UtilityClass
public class BlueReverieExtrasService {

    public static void synchronize(Game game) {
        if (!game.isDynamicDeckOverride()) {
            synchronizeStrategyCardSet(game);
        }
        game.getPlayers().values().forEach(player -> synchronizePlayer(player, game));
        synchronizePromissoryNotes(game);
    }

    public static String getStrategyCardSetID(boolean blueReverieExtras, boolean monumentsMode) {
        if (blueReverieExtras) {
            return monumentsMode ? "bluerevstrat_monuments" : "bluerevstrat";
        }
        return monumentsMode ? "monuments" : "te";
    }

    public static void synchronizeStrategyCardSet(Game game) {
        if (game.isBlueReverieExtras()) {
            game.setStrategyCardSet(getStrategyCardSetID(true, game.isMonumentsMode()));
        } else if ("bluerevstrat".equals(game.getStrategyCardSet().getAlias())
                || "bluerevstrat_monuments".equals(game.getStrategyCardSet().getAlias())) {
            game.setStrategyCardSet(getStrategyCardSetID(false, game.isMonumentsMode()));
        }
    }

    public static void synchronizePlayer(Player player, Game game) {
        boolean useBlueReverieVariants = game.isBlueReverieExtras();
        Mapper.getAbilities().values().stream()
                .filter(ability -> ability.getSource() == ComponentSource.blue_reverie)
                .filter(ability -> ability.getHomebrewReplacesID().isPresent())
                .forEach(ability -> synchronizeAbility(player, ability, useBlueReverieVariants));
        Mapper.getLeaders().values().stream()
                .filter(leader -> leader.getSource() == ComponentSource.blue_reverie)
                .filter(leader -> leader.getHomebrewReplacesID().isPresent())
                .forEach(leader -> synchronizeLeader(player, leader, useBlueReverieVariants));
        Mapper.getUnits().values().stream()
                .filter(unit -> unit.getSource() == ComponentSource.blue_reverie)
                .filter(unit -> unit.getHomebrewReplacesID().isPresent())
                .forEach(unit -> synchronizeUnit(player, unit, useBlueReverieVariants));
    }

    private void synchronizePromissoryNotes(Game game) {
        boolean useBlueReverieVariants = game.isBlueReverieExtras();
        Mapper.getPromissoryNotes().values().stream()
                .filter(promissoryNote -> promissoryNote.getSource() == ComponentSource.blue_reverie)
                .filter(promissoryNote -> promissoryNote.getHomebrewReplacesID().isPresent())
                .forEach(promissoryNote -> synchronizePromissoryNote(game, promissoryNote, useBlueReverieVariants));
    }

    private void synchronizePromissoryNote(Game game, PromissoryNoteModel variant, boolean useVariant) {
        String original = variant.getHomebrewReplacesID()
                .get()
                .replace("<color>", variant.getColor().orElse("<color>"));
        String sourcePromissoryNoteID = useVariant ? original : variant.getAlias();
        String targetPromissoryNoteID = useVariant ? variant.getAlias() : original;
        game.getPlayers()
                .values()
                .forEach(player -> replacePromissoryNote(player, sourcePromissoryNoteID, targetPromissoryNoteID));
    }

    private void replacePromissoryNote(Player player, String sourcePromissoryNoteID, String targetPromissoryNoteID) {
        Integer identifier = player.getPromissoryNotes().get(sourcePromissoryNoteID);
        if (identifier != null) {
            boolean inPlayArea = player.getPromissoryNotesInPlayArea().contains(sourcePromissoryNoteID);
            player.removePromissoryNote(sourcePromissoryNoteID);
            player.setPromissoryNote(targetPromissoryNoteID, identifier);
            if (inPlayArea) {
                player.addPromissoryNoteToPlayArea(targetPromissoryNoteID);
            }
        }
        if (player.ownsPromissoryNote(sourcePromissoryNoteID)) {
            player.removeOwnedPromissoryNoteByID(sourcePromissoryNoteID);
            player.addOwnedPromissoryNoteByID(targetPromissoryNoteID);
        }
    }

    private void synchronizeAbility(Player player, AbilityModel variant, boolean useVariant) {
        String original = variant.getHomebrewReplacesID().get();
        if (useVariant && player.getAbilities().contains(original)) {
            player.removeAbility(original);
            player.addAbility(variant.getAlias());
        } else if (!useVariant && player.getAbilities().contains(variant.getAlias())) {
            player.removeAbility(variant.getAlias());
            player.addAbility(original);
        }
    }

    private void synchronizeLeader(Player player, LeaderModel variant, boolean useVariant) {
        String original = variant.getHomebrewReplacesID().get();
        String sourceLeaderID = useVariant ? original : variant.getId();
        String targetLeaderID = useVariant ? variant.getId() : original;
        player.getLeaderByID(sourceLeaderID).ifPresent(leader -> replaceLeader(player, leader, targetLeaderID));
    }

    private void synchronizeUnit(Player player, UnitModel variant, boolean useVariant) {
        String original = variant.getHomebrewReplacesID().get();
        if (useVariant && player.getUnitsOwned().contains(original)) {
            player.removeOwnedUnitByID(original);
            player.addOwnedUnitByID(variant.getId());
        } else if (!useVariant && player.getUnitsOwned().contains(variant.getId())) {
            player.removeOwnedUnitByID(variant.getId());
            player.addOwnedUnitByID(original);
        }
    }

    private void replaceLeader(Player player, Leader leader, String replacementID) {
        Leader replacement = new Leader(
                replacementID,
                leader.getType(),
                leader.getTgCount(),
                leader.isExhausted(),
                leader.isLocked(),
                leader.isActive());
        player.removeLeader(leader);
        player.addLeader(replacement);
    }
}
