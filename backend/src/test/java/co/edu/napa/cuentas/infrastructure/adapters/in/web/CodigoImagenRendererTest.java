package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodigoImagenRendererTest {
    @Test
    void drawsTheCodeAsAPngWithoutEmbeddingItAsText() throws Exception {
        byte[] png = CodigoImagenRenderer.png("813038");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(300, image.getWidth());
        assertEquals(100, image.getHeight());
        assertFalse(new String(png, java.nio.charset.StandardCharsets.ISO_8859_1).contains("813038"));
    }

    @Test
    void rejectsAnythingButSixDigits() {
        assertThrows(IllegalArgumentException.class, () -> CodigoImagenRenderer.png("12345"));
        assertThrows(IllegalArgumentException.class, () -> CodigoImagenRenderer.png("12a456"));
    }
}
