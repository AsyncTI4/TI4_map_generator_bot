package ti4.service.tactical.postmovement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;

public final class PostMovementAbilities {
    private PostMovementAbilities() {}

    public static final List<PostMovementButtonAbility> ABILITIES = List.of(
            new SardakkCommander(),
            new RaghsCall(),
            new RiftUsed(),
            new CombatDrones(),
            new ShroudOfLith(),
            new MirvedaCommander(),
            new GhostCommander(),
            new KhraskCommander(),
            new NokarAgent(),
            new TnelisAgent(),
            new ZelianAgent(),
            new MuaatHero(),
            new ZelianHero(),
            new SardakkHero(),
            new AtokeraHero(),
            new RohdhnaHero(),
            new Bombardment());

    public static final class SardakkCommander implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "sardakkcommander");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.addAll(ButtonHelperCommanders.getSardakkCommanderButtons(ctx.game, ctx.player, null));
        }
    }

    public static final class RaghsCall implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.getPromissoryNotes().containsKey("ragh");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.addAll(ButtonHelperFactionSpecific.getRaghsCallButtons(ctx.player, ctx.game, ctx.tile));
        }
    }

    public static final class RiftUsed implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return !ctx.game.getStoredValue("possiblyUsedRift").isEmpty();
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.green(
                    ctx.player.finChecker() + "getRiftButtons_" + ctx.tile.getPosition(),
                    "Units Travelled Through Gravity Rift",
                    MiscEmojis.GravityRift));
        }
    }

    public static final class CombatDrones implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(ctx.player, ctx.tile);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "combatDrones", "Use Combat Drones Ability", FactionEmojis.mirveda));
        }
    }

    public static final class ShroudOfLith implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasAbility("shroud_of_lith")
                    && ButtonHelperFactionSpecific.getKolleccReleaseButtons(ctx.player, ctx.game)
                                    .size()
                            > 1;
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc));
            buttons.add(Buttons.gray("refreshLandingButtons", "Refresh Landing Buttons", FactionEmojis.kollecc));
        }
    }

    public static final class MirvedaCommander implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "mirvedacommander");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "offerMirvedaCommander", "Use Mirveda Commander", FactionEmojis.mirveda));
        }
    }

    public static final class GhostCommander implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "ghostcommander");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "placeGhostCommanderFF_" + ctx.tile.getPosition(),
                    "Place Fighter with Creuss Commander",
                    FactionEmojis.Ghost));
        }
    }

    public static final class KhraskCommander implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return !ctx.tile.getPlanetUnitHolders().isEmpty()
                    && ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "khraskcommander");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "placeKhraskCommanderInf_" + ctx.tile.getPosition(),
                    "Place Infantry with Khrask Commander",
                    FactionEmojis.khrask));
        }
    }

    public static final class NokarAgent implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasUnexhaustedLeader("nokaragent")
                    && FoWHelper.playerHasShipsInSystem(ctx.player, ctx.tile);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "exhaustAgent_nokaragent_" + ctx.player.getFaction(),
                    "Use Nokar Agent to Place 1 Destroyer",
                    FactionEmojis.nokar));
        }
    }

    public static final class TnelisAgent implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasUnexhaustedLeader("tnelisagent")
                    && FoWHelper.playerHasShipsInSystem(ctx.player, ctx.tile)
                    && FoWHelper.otherPlayersHaveUnitsInSystem(ctx.player, ctx.tile, ctx.game);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "exhaustAgent_tnelisagent_" + ctx.player.getFaction(), "Use Tnelis Agent", FactionEmojis.tnelis));
        }
    }

    public static final class ZelianAgent implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasUnexhaustedLeader("zelianagent")
                    && ctx.tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, ctx.player.getColor())
                            > 0;
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "exhaustAgent_zelianagent_" + ctx.player.getFaction(),
                    "Use Zelian Agent Yourself",
                    FactionEmojis.zelian));
        }
    }

    public static final class MuaatHero implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasLeaderUnlocked("muaathero")
                    && !ctx.tile.isMecatol()
                    && !ctx.tile.isHomeSystem(ctx.game)
                    && ButtonHelper.getTilesOfPlayersSpecificUnits(ctx.game, ctx.player, UnitType.Warsun)
                            .contains(ctx.tile);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "novaSeed_" + ctx.tile.getPosition(),
                    "Nova Seed This Tile",
                    FactionEmojis.Muaat));
        }
    }

    public static final class ZelianHero implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasLeaderUnlocked("zelianhero")
                    && !ctx.tile.isMecatol()
                    && ButtonHelper.getTilesOfUnitsWithBombard(ctx.player, ctx.game)
                            .contains(ctx.tile);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "celestialImpact_" + ctx.tile.getPosition(),
                    "Celestial Impact This Tile",
                    FactionEmojis.zelian));
        }
    }

    public static final class SardakkHero implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasLeaderUnlocked("sardakkhero")
                    && !ctx.tile.getPlanetUnitHolders().isEmpty();
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "purgeSardakkHero", "Use N'orr Hero", FactionEmojis.Sardakk));
        }
    }

    public static final class AtokeraHero implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasLeaderUnlocked("atokeraherp")
                    && !ctx.tile.getPlanetUnitHolders().isEmpty();
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "purgeAtokeraHero", "Use Atokera Hero", FactionEmojis.atokera));
        }
    }

    public static final class RohdhnaHero implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.player.hasLeaderUnlocked("rohdhnahero");
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "purgeRohdhnaHero", "Use Roh'Dhna Hero", FactionEmojis.rohdhna));
        }
    }

    public static final class Bombardment implements PostMovementButtonAbility {
        public boolean enabled(PostMovementButtonContext ctx) {
            return ctx.tile.getUnitHolders().size() > 1
                    && ButtonHelper.getTilesOfUnitsWithBombard(ctx.player, ctx.game)
                            .contains(ctx.tile);
        }

        public void contribute(PostMovementButtonContext ctx, List<Button> buttons) {
            if (ctx.tile.getUnitHolders().size() > 2) {
                buttons.add(Buttons.gray(
                        "bombardConfirm_combatRoll_" + ctx.tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll BOMBARDMENT"));
            } else {
                buttons.add(Buttons.gray(
                        "combatRoll_" + ctx.tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll BOMBARDMENT"));
            }
        }
    }
}
