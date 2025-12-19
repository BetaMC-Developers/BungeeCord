package net.md_5.bungee.api.config;

import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.Getter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

@Data
public class Icon {

    @Getter
    private byte[] rawData;

    public void load() {
        try {
            File file = new File("icon.png");
            if (!file.exists()) return;
            BufferedImage image = ImageIO.read(file);
            Preconditions.checkArgument(image.getWidth() == 64 && image.getHeight() == 64, "Icon has invalid dimensions, expected 64x64 px");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            rawData = Base64.getEncoder().encode(output.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Could not load icon!", e);
        }
    }
}
