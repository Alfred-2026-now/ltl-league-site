package com.ltl.league.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 上传图片压缩：最长边限制 + JPEG 质量压缩。
 * PNG 会转为同名 .jpg（更利于列表加载），JPEG 则原地覆盖。
 */
@Slf4j
public final class ImageCompressUtil {

    /** 最长边像素上限 */
    public static final int MAX_EDGE = 800;

    /** JPEG 压缩质量（0~1） */
    public static final float JPEG_QUALITY = 0.75f;

    /** 小于该体积且无需缩放/转码时跳过 */
    public static final long SKIP_BELOW_BYTES = 80 * 1024L;

    private static final Set<String> SUPPORTED = Set.of("jpg", "jpeg", "png");

    private ImageCompressUtil() {
    }

    /**
     * 压缩结果：最终落盘路径（PNG 转 JPEG 时会变化）。
     */
    public static final class CompressResult {
        private final boolean changed;
        private final Path path;

        public CompressResult(boolean changed, Path path) {
            this.changed = changed;
            this.path = path;
        }

        public boolean isChanged() {
            return changed;
        }

        public Path getPath() {
            return path;
        }
    }

    /**
     * 压缩图片。JPEG 原地覆盖；PNG 转为同目录同名 .jpg 并删除原 PNG。
     */
    public static CompressResult compress(Path file) throws IOException {
        if (Objects.isNull(file) || !Files.isRegularFile(file)) {
            return new CompressResult(false, file);
        }
        String format = detectFormat(file.getFileName().toString());
        if (Objects.isNull(format)) {
            return new CompressResult(false, file);
        }

        BufferedImage source = ImageIO.read(file.toFile());
        if (Objects.isNull(source)) {
            log.warn("无法读取图片，跳过压缩: {}", file);
            return new CompressResult(false, file);
        }

        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return new CompressResult(false, file);
        }

        boolean needScale = Math.max(srcW, srcH) > MAX_EDGE;
        boolean pngToJpeg = "png".equals(format);
        long originalSize = Files.size(file);
        if (!needScale && !pngToJpeg && originalSize <= SKIP_BELOW_BYTES) {
            return new CompressResult(false, file);
        }

        int targetW = srcW;
        int targetH = srcH;
        if (needScale) {
            double scale = (double) MAX_EDGE / (double) Math.max(srcW, srcH);
            targetW = Math.max(1, (int) Math.round(srcW * scale));
            targetH = Math.max(1, (int) Math.round(srcH * scale));
        }

        BufferedImage resized = resizeToRgb(source, targetW, targetH);
        Path output = pngToJpeg ? withExtension(file, ".jpg") : file;
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString() + ".", ".compress-tmp");
        try {
            writeJpeg(resized, temp, JPEG_QUALITY);
            long newSize = Files.size(temp);
            if (!pngToJpeg && newSize >= originalSize && !needScale) {
                Files.deleteIfExists(temp);
                return new CompressResult(false, file);
            }
            try {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
            ensureWorldReadable(output);
            if (pngToJpeg && !file.equals(output)) {
                Files.deleteIfExists(file);
            }
            log.info("图片已压缩: {} -> {} ({}KB -> {}KB, {}x{} -> {}x{})",
                    file.getFileName(),
                    output.getFileName(),
                    originalSize / 1024,
                    newSize / 1024,
                    srcW, srcH, targetW, targetH);
            return new CompressResult(true, output);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * 兼容旧调用：仅关心是否写入成功。
     */
    public static boolean compressInPlace(Path file) throws IOException {
        return compress(file).isChanged();
    }

    /**
     * 从 Multipart 落盘后立即压缩；失败保留原图。
     *
     * @return 最终文件路径（可能从 png 变为 jpg）
     */
    public static Path compressUploadedFile(Path file) {
        try {
            Path result = compress(file).getPath();
            ensureWorldReadable(result);
            return result;
        } catch (Exception e) {
            log.warn("上传后压缩失败，保留原图: {}", file, e);
            ensureWorldReadable(file);
            return file;
        }
    }

    static String detectFormat(String filename) {
        if (Objects.isNull(filename) || !filename.contains(".")) {
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return "jpg";
        }
        if (SUPPORTED.contains(ext)) {
            return ext;
        }
        return null;
    }

    static Path withExtension(Path file, String extension) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return file.resolveSibling(base + extension);
    }

    /**
     * 压缩写盘后默认可能是 600，Nginx(www-data) 无法读取；统一设为 644。
     */
    static void ensureWorldReadable(Path file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ
            );
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException e) {
            log.warn("设置图片可读权限失败: {}", file, e);
        }
    }

    private static BufferedImage resizeToRgb(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("当前环境不支持 JPEG 写入");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        try (OutputStream os = Files.newOutputStream(target);
             ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
