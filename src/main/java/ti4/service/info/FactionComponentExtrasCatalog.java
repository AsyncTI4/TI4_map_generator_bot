package ti4.service.info;

import java.util.List;
import ti4.discord.interactions.buttons.handlers.faction.discordantstars.axis.AxisComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.discordantstars.lizho.LizhoComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.discordantstars.olradin.OlradinComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.discordantstars.toldar.ToldarComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.admins.AdminsComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom.BloomComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir.ChoirComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep.DeepComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron.IronComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo.MigoComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.BelkoseaComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.XinComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix.XyrixComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir.VaettirComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum.ArcanumComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha.MorphaComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.scrapyard.ScrapyardComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi.ArvaxiComponentExtras;
import ti4.discord.interactions.buttons.handlers.faction.thundersedge.firmament.FirmamentComponentExtras;

public final class FactionComponentExtrasCatalog {
    private FactionComponentExtrasCatalog() {}

    public static final List<FactionComponentExtras> EXTRAS = List.of(
            new AdminsComponentExtras(),
            new AeternaComponentExtras(),
            new ArcanumComponentExtras(),
            new ArvaxiComponentExtras(),
            new AxisComponentExtras(),
            new BelkoseaComponentExtras(),
            new BloomComponentExtras(),
            new ChoirComponentExtras(),
            new DeepComponentExtras(),
            new FirmamentComponentExtras(),
            new IronComponentExtras(),
            new LizhoComponentExtras(),
            new MigoComponentExtras(),
            new MorphaComponentExtras(),
            new OlradinComponentExtras(),
            new ScrapyardComponentExtras(),
            new StonebornComponentExtras(),
            new ThurvialiComponentExtras(),
            new ToldarComponentExtras(),
            new VaettirComponentExtras(),
            new XinComponentExtras(),
            new XyrixComponentExtras());
}
