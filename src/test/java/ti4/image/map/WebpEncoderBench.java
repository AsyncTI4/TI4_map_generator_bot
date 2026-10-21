package ti4.image.map;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.image.io.ImageEncoder;
import ti4.testUtils.BaseTi4Test;

/**
 * Benchmark of libwebp method (0 to 6) and thread level for the map canvas of DisplayType.all. Production runs two
 * render threads, so every configuration is measured with two encodes of the same canvas running at once. Very large
 * canvases only get a reduced matrix so the run stays practical.
 *
 * <pre>
 * mvn -o test -Dtest=WebpEncoderBench -Dgolden.bench.saves=DIR -Dgolden.bench.out=FILE [-Dgolden.only=a,b]
 *     [-Dgolden.bench.crops=DIR] -DargLine=-Xmx10g
 * </pre>
 */
@EnabledIfSystemProperty(named = "golden.bench.saves", matches = ".+")
class WebpEncoderBench extends BaseTi4Test {

    private static final long LARGE_CANVAS_PIXELS = 40_000_000L;
    private static final long PSNR_CANVAS_PIXELS = 20_000_000L;
    private static final int PSNR_GAME_LIMIT = 5;
    private static final int WEBP_MAX_DIMENSION = 16_383;
    private static final int CROP_SIZE = 700;

    private record Result(int method, int threadLevel, long millis, int bytes) {}

    @Test
    void benchmarkCorpus() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.bench.saves"));
        Path out = Path.of(System.getProperty("golden.bench.out"));
        String cropDir = System.getProperty("golden.bench.crops");
        String only = System.getProperty("golden.only", "");
        List<Path> saves;
        try (Stream<Path> files = Files.list(savesDir)) {
            saves = files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> only.isBlank()
                            || List.of(only.split(","))
                                    .contains(path.getFileName().toString().replace(".txt", "")))
                    .sorted()
                    .toList();
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int psnrGames = 0;
        StringBuilder report = new StringBuilder("game\twidth\theight\tmethod\tthreadLevel\tmillis\tbytes\tpsnr\n");
        try {
            for (Path save : saves) {
                String name = save.getFileName().toString().replace(".txt", "");
                BufferedImage canvas;
                try {
                    canvas = renderCanvas(save);
                } catch (Exception e) {
                    System.out.println("[bench] render failed, skipped " + name);
                    continue;
                }
                psnrGames++;
                if (canvas.getWidth() > WEBP_MAX_DIMENSION || canvas.getHeight() > WEBP_MAX_DIMENSION) {
                    System.out.println("[bench] skipped (jpg in production) " + name);
                    continue;
                }
                long pixels = (long) canvas.getWidth() * canvas.getHeight();
                boolean large = pixels > LARGE_CANVAS_PIXELS;
                int production = ImageEncoder.webp(canvas).length;
                int method4 = encode(canvas, 4, 0).length;
                System.out.println("[bench] production writer " + production + " bytes, method 4 thread 0 " + method4);
                for (int method : large ? new int[] {0, 2, 4, 6} : new int[] {0, 1, 2, 3, 4, 5, 6}) {
                    for (int threadLevel : new int[] {0, 1}) {
                        Result result = concurrentEncode(pool, canvas, method, threadLevel, large ? 1 : 2);
                        String psnr = "";
                        if (threadLevel == 0 && pixels <= PSNR_CANVAS_PIXELS && psnrGames <= PSNR_GAME_LIMIT) {
                            psnr = String.format("%.2f", psnr(canvas, encode(canvas, method, 0)));
                        }
                        report.append(String.format(
                                "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%s%n",
                                name,
                                canvas.getWidth(),
                                canvas.getHeight(),
                                method,
                                threadLevel,
                                result.millis(),
                                result.bytes(),
                                psnr));
                    }
                }
                if (cropDir != null && pixels <= PSNR_CANVAS_PIXELS) {
                    writeCrops(canvas, name, Path.of(cropDir));
                }
                System.out.println("[bench] " + name + " " + canvas.getWidth() + "x" + canvas.getHeight());
                Files.writeString(out, report.toString());
            }
        } finally {
            pool.shutdown();
        }
        Files.writeString(out, report.toString());
    }

    private static BufferedImage renderCanvas(Path save) throws Exception {
        Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
        Method readGame = loader.getDeclaredMethod("readGame", File.class);
        readGame.setAccessible(true);
        Game game = (Game) readGame.invoke(null, save.toFile());
        try (AutoCloseable ignored = RenderRandom.neutral()) {
            return MapRenderer.paint(
                    MapRenderer.resolve(new MapRenderRequest(game, DisplayType.all, null, null)),
                    RenderTimings.disabled());
        }
    }

    private static Result concurrentEncode(
            ExecutorService pool, BufferedImage canvas, int method, int threadLevel, int reps) throws Exception {
        long bestMillis = Long.MAX_VALUE;
        int bytes = 0;
        for (int rep = 0; rep < reps; rep++) {
            Callable<long[]> task = () -> {
                long start = System.nanoTime();
                byte[] encoded = encode(canvas, method, threadLevel);
                return new long[] {(System.nanoTime() - start) / 1_000_000, encoded.length};
            };
            Future<long[]> first = pool.submit(task);
            Future<long[]> second = pool.submit(task);
            long[] a = first.get();
            long[] b = second.get();
            long millis = (a[0] + b[0]) / 2;
            if (millis < bestMillis) {
                bestMillis = millis;
            }
            bytes = (int) a[1];
        }
        return new Result(method, threadLevel, bestMillis, bytes);
    }

    private static byte[] encode(BufferedImage image, int method, int threadLevel) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                var output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(CompressionType.Lossy);
            param.setUseSharpYUV(false);
            param.setAlphaCompressionAlgorithm(0);
            param.setMethod(method);
            param.setThreadLevel(threadLevel);
            writer.write(null, new IIOImage(image, null, null), param);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static double psnr(BufferedImage original, byte[] encoded) throws Exception {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        double squares = 0;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int a = original.getRGB(x, y);
                int b = decoded.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    int delta = ((a >> shift) & 0xFF) - ((b >> shift) & 0xFF);
                    squares += (double) delta * delta;
                }
            }
        }
        double mse = squares / ((double) original.getWidth() * original.getHeight() * 3);
        return mse == 0 ? 99 : 10 * Math.log10(255.0 * 255.0 / mse);
    }

    private static void writeCrops(BufferedImage canvas, String name, Path dir) throws Exception {
        Files.createDirectories(dir);
        int x = 0;
        int y = Math.max(0, canvas.getHeight() - 1500);
        int width = Math.min(CROP_SIZE, canvas.getWidth());
        int height = Math.min(CROP_SIZE, canvas.getHeight() - y);
        ImageIO.write(
                canvas.getSubimage(x, y, width, height),
                "png",
                dir.resolve(name + "-original.png").toFile());
        List<Integer> methods = new ArrayList<>(List.of(0, 4, 6));
        for (int method : methods) {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encode(canvas, method, 0)));
            ImageIO.write(
                    decoded.getSubimage(x, y, width, height),
                    "png",
                    dir.resolve(name + "-method" + method + ".png").toFile());
        }
    }
}
