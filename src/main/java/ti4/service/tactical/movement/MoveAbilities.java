package ti4.service.tactical.movement;

import java.util.List;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;

public final class MoveAbilities {
    private MoveAbilities() {}

    public static final List<MoveAbility> ABILITIES = List.of(
            new SaarAgent(),
            new BelkoseaAgent(),
            new QhetAgent(),
            new DominusOrb(),
            new EyeOfVogul(),
            new AbsolLuxarchTreatise(),
            new GhostAgent(),
            new Aetherstream(),
            new EmergencyModifications(),
            new RealityFieldImpactor(),
            new GravityDrive(),
            new NavigationRelays(),
            new LightWaveDeflector(),
            new LightningDrives(),
            new MidasTurbine(),
            new VaylerianCommander(),
            new VaylerianHero(),
            new UydaiHero(),
            new GhostMech(),
            new NivynMech(),
            new WraithEngine(),
            new EkoPlanetAbility());

    public static final class SaarAgent implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasUnexhaustedLeader("saaragent");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustAgent_saaragent", "Use Saar Agent", FactionEmojis.Saar));
        }
    }

    public static final class BelkoseaAgent implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasUnexhaustedLeader("belkoseaagent");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustAgent_belkoseaagent", "Use Belkosea Agent", FactionEmojis.belkosea));
        }
    }

    public static final class QhetAgent implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasUnexhaustedLeader("qhetagent")
                    && ctx.active != null
                    && !ctx.active.isHomeSystem(ctx.game)
                    && FoWHelper.otherPlayersHaveShipsInSystem(ctx.player, ctx.active, ctx.game);
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustAgent_qhetagent", "Use Qhet Agent", FactionEmojis.qhet));
        }
    }

    public static final class DominusOrb implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasRelic("dominusorb");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("dominusOrb", "Purge Dominus Orb", ExploreEmojis.Relic));
        }
    }

    public static final class EyeOfVogul implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasRelic("eye_of_vogul");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("eyeOfVogul", "Purge Eye of Vogul", ExploreEmojis.Relic));
        }
    }

    public static final class AbsolLuxarchTreatise implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasRelicReady("absol_luxarchtreatise");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "exhaustRelic_absol_luxarchtreatise", "Exhaust Luxarch Treatise", ExploreEmojis.Relic));
        }
    }

    public static final class GhostAgent implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasUnexhaustedLeader("ghostagent")
                    && FoWHelper.doesTileHaveWHs(ctx.game, ctx.activeSystem);
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustAgent_ghostagent", "Use Creuss Agent", FactionEmojis.Ghost));
        }
    }

    public static final class Aetherstream implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTech("as")
                    && FoWHelper.isTileAdjacentToAnAnomaly(ctx.game, ctx.activeSystem, ctx.player);
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("declareUse_Aetherstream", "Declare Aetherstream", FactionEmojis.Empyrean));
        }
    }

    public static final class EmergencyModifications implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTech("dstoldb");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "declareUse_Emergency Modifications", "Emergency Modifications", FactionEmojis.toldar));
        }
    }

    public static final class RealityFieldImpactor implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTech("dspharb");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    "declareUse_Reality Field Impactor", "Declare Reality Field Impactor", FactionEmojis.pharadn));
        }
    }

    public static final class GravityDrive implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTech("baldrick_gd");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustTech_baldrick_gd", "Exhaust Gravity Drive", SourceEmojis.IgnisAurora));
        }
    }

    public static final class NavigationRelays implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTechReady("dsuydab");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustTech_dsuydab", "Exhaust Navigation Relays", FactionEmojis.uydai));
        }
    }

    public static final class LightWaveDeflector implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasTech("baldrick_lwd");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(
                    Buttons.gray("exhaustTech_baldrick_lwd", "Exhaust Light/Wave Deflector", SourceEmojis.IgnisAurora));
        }
    }

    public static final class LightningDrives implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.getTechs().contains("dsgledb");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.green(
                    ctx.player.finChecker() + "declareUse_Lightning",
                    "Declare Lightning Drives",
                    FactionEmojis.gledge));
        }
    }

    public static final class MidasTurbine implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.getTechs().contains("dsvadeb")
                    && !ctx.player.getExhaustedTechs().contains("dsvadeb");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.green(
                    ctx.player.finChecker() + "exhaustTech_dsvadeb", "Exhaust Midas Turbine", FactionEmojis.vaden));
        }
    }

    public static final class VaylerianCommander implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.game.playerHasLeaderUnlockedOrAlliance(ctx.player, "vayleriancommander");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(
                    Buttons.gray("declareUse_Vaylerian Commander", "Use Vaylerian Commander", FactionEmojis.vaylerian));
        }
    }

    public static final class VaylerianHero implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.hasLeaderUnlocked("vaylerianhero");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "purgeVaylerianHero", "Use Vaylerian Hero", FactionEmojis.vaylerian));
        }
    }

    public static final class UydaiHero implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.active != null
                    && !ctx.active.isHomeSystem(ctx.game)
                    && ctx.player.hasLeaderUnlocked("uydaihero");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.blue(
                    ctx.player.finChecker() + "purgeUydaiHero", "Use Uydai Hero", FactionEmojis.vaylerian));
        }
    }

    public static final class GhostMech implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.ownsUnit("ghost_mech")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(ctx.game, ctx.player, "mech") > 0;
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("creussMechStep1_", "Use Creuss Mech", FactionEmojis.Ghost));
        }
    }

    public static final class NivynMech implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return (ctx.player.ownsUnit("nivyn_mech")
                            && ButtonHelper.getTilesOfPlayersSpecificUnits(ctx.game, ctx.player, UnitType.Mech)
                                    .contains(ctx.active))
                    || ctx.player.ownsUnit("nivyn_mech2");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", FactionEmojis.nivyn));
        }
    }

    public static final class WraithEngine implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.active != null && ctx.player.hasTech("dslihb") && !ctx.active.isHomeSystem(ctx.game);
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray("exhaustTech_dslihb", "Exhaust Wraith Engine", FactionEmojis.lizho));
        }
    }

    public static final class EkoPlanetAbility implements MoveAbility {
        public boolean enabled(MoveContext ctx) {
            return ctx.player.getPlanets().contains("eko")
                    && !ctx.player.getExhaustedPlanetsAbilities().contains("eko");
        }

        public void contribute(MoveContext ctx, List<Button> buttons) {
            buttons.add(Buttons.gray(
                    ctx.player.finChecker() + "planetAbilityExhaust_" + "eko",
                    "Use Eko's Ability To Ignore Anomalies"));
        }
    }
}
