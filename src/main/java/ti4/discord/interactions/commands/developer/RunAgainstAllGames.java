package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = removeBlackSpectrumGenericPNs(game);
                    changed |= revertOtherBlackSpectrumComponents(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Reverted stray Black Spectrum components to what they replaced.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    // Black Spectrum's colorable Political Secret/Support for the Throne replacements were dealt
    // to every player in every game, since nothing gated them behind an actual "is Black Spectrum
    // enabled" check. Strip any stray copies out of every player's hand and owned-PN pool, wherever
    // they ended up (including after being traded away from whoever originally received them).
    static boolean removeBlackSpectrumGenericPNs(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            for (String pnID : new ArrayList<>(player.getPromissoryNotes().keySet())) {
                if (pnID.endsWith("_bsp_ps") || pnID.endsWith("_bsp_sftt")) {
                    player.removePromissoryNote(pnID);
                    changed = true;
                }
            }
            for (String pnID : new ArrayList<>(player.getPromissoryNotesOwned())) {
                if (pnID.endsWith("_bsp_ps") || pnID.endsWith("_bsp_sftt")) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                    changed = true;
                }
            }
        }
        return changed;
    }

    // Some of Black Spectrum's other replacement units/leaders/techs/abilities (each declaring
    // homebrewReplacesID) can also end up owned by a player with no way for that to have been an
    // intentional choice, since nothing gates Black Spectrum content behind an "is it enabled" check
    // yet. For every player, swap any such stray black_spectrum component back for the original
    // component it claims to replace.
    static boolean revertOtherBlackSpectrumComponents(Game game) {
        boolean changed = false;
        for (Player player : game.getPlayers().values()) {
            changed |= revertUnitsOwned(player);
            changed |= revertLeaders(player);
            changed |= revertTechList(player.getTechs());
            changed |= revertTechList(player.getFactionTechs());
            changed |= revertTechList(player.getExhaustedTechs());
            changed |= revertTechList(player.getPurgedTechs());
            changed |= revertAbilities(player.getAbilities());
            changed |= revertAbilities(player.getExhaustedAbilities());
        }
        return changed;
    }

    private static boolean isStrayBlackSpectrum(ComponentSource source, Optional<String> replacesID) {
        return source == ComponentSource.black_spectrum && replacesID.isPresent();
    }

    private static boolean revertUnitsOwned(Player player) {
        boolean changed = false;
        for (String unitId : new ArrayList<>(player.getUnitsOwned())) {
            UnitModel model = Mapper.getUnit(unitId);
            if (model != null && isStrayBlackSpectrum(model.getSource(), model.getHomebrewReplacesID())) {
                player.removeOwnedUnitByID(unitId);
                player.addOwnedUnitByID(model.getHomebrewReplacesID().get());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean revertLeaders(Player player) {
        boolean changed = false;
        for (Leader leader : new ArrayList<>(player.getLeaders())) {
            LeaderModel model = Mapper.getLeader(leader.getId());
            if (model != null && isStrayBlackSpectrum(model.getSource(), model.getHomebrewReplacesID())) {
                player.removeLeader(leader.getId());
                player.addLeader(model.getHomebrewReplacesID().get());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean revertTechList(List<String> techIds) {
        boolean changed = false;
        for (int i = 0; i < techIds.size(); i++) {
            TechnologyModel model = Mapper.getTech(techIds.get(i));
            if (model != null && isStrayBlackSpectrum(model.getSource(), model.getHomebrewReplacesID())) {
                techIds.set(i, model.getHomebrewReplacesID().get());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean revertAbilities(Set<String> abilityIds) {
        boolean changed = false;
        for (String abilityId : new ArrayList<>(abilityIds)) {
            AbilityModel model = Mapper.getAbility(abilityId);
            if (model != null && isStrayBlackSpectrum(model.getSource(), model.getHomebrewReplacesID())) {
                abilityIds.remove(abilityId);
                abilityIds.add(model.getHomebrewReplacesID().get());
                changed = true;
            }
        }
        return changed;
    }
}
