package com.ltl.league.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCompressUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void detectFormatRecognizesJpegAndPng() {
        assertEquals("jpg", ImageCompressUtil.detectFormat("a.JPG"));
        assertEquals("jpg", ImageCompressUtil.detectFormat("a.jpeg"));
        assertEquals("png", ImageCompressUtil.detectFormat("logo.png"));
        assertNull(ImageCompressUtil.detectFormat("note.txt"));
        assertNull(ImageCompressUtil.detectFormat("noext"));
    }

    @Test
    void compressScalesOversizedJpegInPlace() throws Exception {
        Path file = tempDir.resolve("big.jpg");
        writeSolidJpeg(file, 1600, 1200, Color.BLUE);

        long before = Files.size(file);
        ImageCompressUtil.CompressResult result = ImageCompressUtil.compress(file);

        assertTrue(result.isChanged());
        assertEquals(file, result.getPath());
        BufferedImage after = ImageIO.read(result.getPath().toFile());
        assertEquals(800, Math.max(after.getWidth(), after.getHeight()));
        assertTrue(Files.size(result.getPath()) < before);
    }

    @Test
    void compressSkipsSmallAlreadySizedJpeg() throws Exception {
        Path file = tempDir.resolve("small.jpg");
        writeSolidJpeg(file, 200, 150, Color.RED);
        assertFalse(ImageCompressUtil.compress(file).isChanged());
    }

    @Test
    void compressConvertsOpaquePngToJpeg() throws Exception {
        Path file = tempDir.resolve("badge.png");
        writeSolidPng(file, 1200, 1200, new Color(0, 128, 255));

        ImageCompressUtil.CompressResult result = ImageCompressUtil.compress(file);
        assertTrue(result.isChanged());
        assertTrue(result.getPath().getFileName().toString().endsWith(".jpg"));
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(result.getPath()));
        BufferedImage after = ImageIO.read(result.getPath().toFile());
        assertEquals(800, Math.max(after.getWidth(), after.getHeight()));
    }

    @Test
    void compressPreservesTransparentPng() throws Exception {
        Path file = tempDir.resolve("badge.png");
        writeSolidPng(file, 1200, 1200, new Color(0, 128, 255, 180));

        ImageCompressUtil.CompressResult result = ImageCompressUtil.compress(file);
        assertTrue(result.isChanged());
        assertTrue(result.getPath().getFileName().toString().endsWith(".png"));
        assertEquals(file, result.getPath());
        BufferedImage after = ImageIO.read(result.getPath().toFile());
        assertEquals(800, Math.max(after.getWidth(), after.getHeight()));
        assertTrue(ImageCompressUtil.hasTransparency(after));
    }

    private static void writeSolidJpeg(Path file, int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "jpg", file.toFile());
    }

    private static void writeSolidPng(Path file, int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}
