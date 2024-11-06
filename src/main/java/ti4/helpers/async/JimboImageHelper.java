package ti4.helpers.async;

import net.dv8tion.jda.api.utils.FileUpload;
import ti4.generator.DrawingUtil;
import ti4.generator.MapGenerator;
import ti4.generator.MapGenerator.HorizontalAlign;
import ti4.generator.MapGenerator.VerticalAlign;
import ti4.generator.Mapper;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
import ti4.model.BorderAnomalyModel.BorderAnomalyType;
import ti4.model.TileModel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// Jazz's Interactive Map Builder
public class JimboImageHelper {

    public static FileUpload tilesImage(List<TileModel> tiles) {
        return generateImage(tiles, TileModel::getTilePath, TileModel::getAlias);
    }

    public static FileUpload tokenAndAttachmentImage(List<String> tokens) {
        Function<String, String> getTokenName = tok -> {
            String name = Mapper.getTokensToName().get(tok);
            return (name != null) ? name : tok;
        };
        return generateImage(tokens, Mapper::getTokenPath, getTokenName);
    }

    public static FileUpload borderAnomalyImage(List<BorderAnomalyType> anomalies) {
        return generateImage(anomalies, BorderAnomalyType::getImageFilePath, BorderAnomalyType::getName);
    }

    // ------------------------------------------------------------------------------------------------------------------------------------------------
    // Image Generation Helper Functions
    // ------------------------------------------------------------------------------------------------------------------------------------------------
    private static <T> FileUpload generateImage(List<T> models, Function<T, String> getImgPath, Function<T, String> getDisplayName) {
        List<BufferedImage> images = new ArrayList<>();
        for (T model : models) {
            BufferedImage img = ImageHelper.square(ImageHelper.read(getImgPath.apply(model)));
            Graphics2D g2 = img.createGraphics();
            g2.setFont(Storage.getFont32());
            DrawingUtil.superDrawString(g2, getDisplayName.apply(model), img.getWidth() / 2, 0, null, HorizontalAlign.Center, VerticalAlign.Top, null, null);
            images.add(img);
        }
        return layoutImagesAndUpload(images);
    }

    private static FileUpload layoutImagesAndUpload(List<BufferedImage> images) {
        if (images.isEmpty()) return null;
        int size = images.stream().map(BufferedImage::getWidth).max((a, b) -> a > b ? a : b).orElse(600);
        int n = 5;
        int m = (images.size() + n - 1) / n;
        int i = 0;
        BufferedImage newImage = new BufferedImage(size * n, size * m, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newImage.createGraphics();
        for (BufferedImage img : images) {
            int x = i % n;
            int y = i / n;
            int xoff = (size - img.getWidth()) / 2;
            g2.drawImage(img, x * size + xoff, y * size, null);
            i++;
        }
        return MapGenerator.createFileUpload(newImage, 1.0f, "jimboStuff");
    }
}
