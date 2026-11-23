package co.edu.napa.cuentas.infrastructure.adapters.in.web;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dibuja el código como PNG con trazos a mano alzada, giros y ruido. No usa fuentes del sistema,
 * así funciona en contenedores sin fuentes instaladas, y no deja texto que se pueda copiar.
 */
final class CodigoImagenRenderer {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 100;
    private static final int DIGIT_WIDTH = 30;
    private static final int DIGIT_HEIGHT = 52;

    /** Trazos de cada dígito en coordenadas de 0 a 1 (x hacia la derecha, y hacia abajo). */
    private static final double[][][] DIGITS = {
            {{.5, 0, .15, .15, .05, .5, .15, .85, .5, 1, .85, .85, .95, .5, .85, .15, .5, 0}},
            {{.25, .2, .55, 0, .55, 1}, {.25, 1, .85, 1}},
            {{.1, .25, .3, .03, .65, .02, .88, .2, .85, .42, .1, 1, .9, 1}},
            {{.1, .1, .4, 0, .8, .08, .85, .3, .45, .48}, {.45, .48, .88, .62, .88, .85, .55, 1, .1, .92}},
            {{.7, 1, .7, 0, .05, .68, .95, .68}},
            {{.88, 0, .18, 0, .12, .45, .5, .38, .85, .52, .88, .8, .6, 1, .1, .92}},
            {{.8, .05, .45, .02, .15, .3, .08, .7, .25, .97, .6, 1, .88, .8, .82, .55, .5, .45, .15, .6}},
            {{.08, 0, .92, 0, .4, 1}, {.3, .5, .75, .5}},
            {{.5, .47, .2, .35, .2, .1, .5, 0, .8, .1, .8, .35, .5, .47, .12, .65, .15, .9, .5, 1, .85, .9,
                    .88, .65, .5, .47}},
            {{.85, .4, .5, .55, .15, .4, .15, .12, .5, 0, .85, .15, .88, .5, .75, .85, .45, 1, .15, .95}},
    };

    private CodigoImagenRenderer() {
    }

    static byte[] png(String codigo) {
        if (codigo == null || !codigo.matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("The code must contain exactly six digits");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            for (int i = 0; i < 260; i++) {
                g.setColor(new Color(150 + random.nextInt(90), 150 + random.nextInt(90), 150 + random.nextInt(90)));
                g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }
            dibujarRuido(g, random, 3, new BasicStroke(1.5f));

            int margin = (WIDTH - codigo.length() * (DIGIT_WIDTH + 14)) / 2;
            for (int i = 0; i < codigo.length(); i++) {
                int digit = codigo.charAt(i) - '0';
                double x = margin + i * (DIGIT_WIDTH + 14) + random.nextDouble(-4, 4);
                double y = (HEIGHT - DIGIT_HEIGHT) / 2.0 + random.nextDouble(-8, 8);
                AffineTransform previous = g.getTransform();
                g.translate(x + DIGIT_WIDTH / 2.0, y + DIGIT_HEIGHT / 2.0);
                g.rotate(random.nextDouble(-0.3, 0.3));
                g.shear(random.nextDouble(-0.15, 0.15), 0);
                g.translate(-DIGIT_WIDTH / 2.0, -DIGIT_HEIGHT / 2.0);
                g.setColor(new Color(20 + random.nextInt(60), 30 + random.nextInt(60), 70 + random.nextInt(80)));
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (double[] stroke : DIGITS[digit]) {
                    g.draw(trazo(stroke, random));
                }
                g.setTransform(previous);
            }
            dibujarRuido(g, random, 2, new BasicStroke(2f));
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path2D trazo(double[] points, ThreadLocalRandom random) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.length; i += 2) {
            double px = points[i] * DIGIT_WIDTH + random.nextDouble(-1.2, 1.2);
            double py = points[i + 1] * DIGIT_HEIGHT + random.nextDouble(-1.2, 1.2);
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        return path;
    }

    private static void dibujarRuido(Graphics2D g, ThreadLocalRandom random, int lines, BasicStroke stroke) {
        g.setStroke(stroke);
        for (int i = 0; i < lines; i++) {
            g.setColor(new Color(90 + random.nextInt(100), 90 + random.nextInt(100), 120 + random.nextInt(100)));
            Path2D curve = new Path2D.Double();
            curve.moveTo(0, random.nextInt(HEIGHT));
            curve.curveTo(
                    WIDTH / 3.0, random.nextInt(HEIGHT),
                    2 * WIDTH / 3.0, random.nextInt(HEIGHT),
                    WIDTH, random.nextInt(HEIGHT)
            );
            g.draw(curve);
        }
    }
}
