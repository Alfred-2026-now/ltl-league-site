package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.entity.Attachment;
import com.ltl.league.entity.Prize;
import com.ltl.league.entity.Team;
import com.ltl.league.mapper.AttachmentMapper;
import com.ltl.league.mapper.PrizeMapper;
import com.ltl.league.mapper.TeamMapper;
import com.ltl.league.util.ImageCompressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 扫描上传目录，对历史图片做压缩（PNG 转 JPEG 时同步更新库内 URL）。
 */
@Slf4j
@Service
public class UploadCompressService {

    private final Path configuredUploadDir;
    private final PrizeMapper prizeMapper;
    private final TeamMapper teamMapper;
    private final AttachmentMapper attachmentMapper;

    public UploadCompressService(@Value("${ltl.upload.dir:/var/www/ltl-league/uploads}") String uploadDir,
                                 PrizeMapper prizeMapper,
                                 TeamMapper teamMapper,
                                 AttachmentMapper attachmentMapper) {
        this.configuredUploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.prizeMapper = prizeMapper;
        this.teamMapper = teamMapper;
        this.attachmentMapper = attachmentMapper;
    }

    /**
     * 递归压缩 uploads 下历史图片（奖品 / 队徽 / 战绩截图）。
     *
     * @return scanned / compressed / skipped / failed / bytesSaved / urlsUpdated / scanRoot
     */
    public Map<String, Object> compressExistingUploads() {
        Path scanRoot = resolveScanRoot(configuredUploadDir);
        Map<String, Object> summary = new HashMap<>();
        summary.put("scanRoot", scanRoot.toString());

        if (!Files.isDirectory(scanRoot)) {
            summary.put("scanned", 0);
            summary.put("compressed", 0);
            summary.put("skipped", 0);
            summary.put("failed", 0);
            summary.put("urlsUpdated", 0);
            summary.put("bytesSaved", 0L);
            summary.put("message", "上传目录不存在: " + scanRoot);
            return summary;
        }

        int scanned = 0;
        int compressed = 0;
        int skipped = 0;
        int failed = 0;
        int urlsUpdated = 0;
        long bytesSaved = 0L;

        try (Stream<Path> walk = Files.walk(scanRoot)) {
            List<Path> images = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isCompressibleImage)
                    .collect(Collectors.toList());
            for (Path file : images) {
                scanned++;
                try {
                    long before = Files.size(file);
                    ImageCompressUtil.CompressResult result = ImageCompressUtil.compress(file);
                    if (result.isChanged()) {
                        compressed++;
                        long after = Files.size(result.getPath());
                        bytesSaved += Math.max(0L, before - after);
                        urlsUpdated += rewriteDbUrls(file, result.getPath());
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("历史图片压缩失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            summary.put("message", "扫描失败: " + e.getMessage());
            log.error("扫描上传目录失败: {}", scanRoot, e);
        }

        summary.put("scanned", scanned);
        summary.put("compressed", compressed);
        summary.put("skipped", skipped);
        summary.put("failed", failed);
        summary.put("urlsUpdated", urlsUpdated);
        summary.put("bytesSaved", bytesSaved);
        return summary;
    }

    /**
     * 若配置指向 uploads/prizes，则上溯到 uploads，以便覆盖 teams 与战绩截图。
     */
    static Path resolveScanRoot(Path configured) {
        if (configured.getFileName() != null
                && "prizes".equalsIgnoreCase(configured.getFileName().toString())
                && configured.getParent() != null) {
            return configured.getParent();
        }
        return configured;
    }

    private boolean isCompressibleImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains(".compress-tmp")) {
            return false;
        }
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    private int rewriteDbUrls(Path oldPath, Path newPath) {
        if (oldPath.equals(newPath)) {
            return 0;
        }
        String oldName = oldPath.getFileName().toString();
        String newName = newPath.getFileName().toString();
        int updated = 0;

        List<Prize> prizes = prizeMapper.selectList(new LambdaQueryWrapper<Prize>()
                .like(Prize::getImageUrl, oldName));
        for (Prize prize : prizes) {
            if (prize.getImageUrl() == null) {
                continue;
            }
            prize.setImageUrl(prize.getImageUrl().replace(oldName, newName));
            prizeMapper.updateById(prize);
            updated++;
        }

        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .like(Team::getLogoUrl, oldName));
        for (Team team : teams) {
            if (team.getLogoUrl() == null) {
                continue;
            }
            team.setLogoUrl(team.getLogoUrl().replace(oldName, newName));
            teamMapper.updateById(team);
            updated++;
        }

        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .and(w -> w.like(Attachment::getUrl, oldName)
                        .or()
                        .like(Attachment::getFilePath, oldName)));
        for (Attachment attachment : attachments) {
            boolean changed = false;
            if (attachment.getUrl() != null && attachment.getUrl().contains(oldName)) {
                attachment.setUrl(attachment.getUrl().replace(oldName, newName));
                changed = true;
            }
            if (attachment.getFilePath() != null && attachment.getFilePath().contains(oldName)) {
                attachment.setFilePath(attachment.getFilePath().replace(oldName, newName));
                changed = true;
            }
            if (changed) {
                attachmentMapper.updateById(attachment);
                updated++;
            }
        }
        return updated;
    }
}
