package com.ltl.league.controller;

import com.ltl.league.common.Result;
import com.ltl.league.dto.CreatePrizeRequest;
import com.ltl.league.dto.PrizeVO;
import com.ltl.league.dto.UpdatePrizeRequest;
import com.ltl.league.service.PrizeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/prizes")
public class PrizeController {

    private final PrizeService prizeService;

    @Value("${ltl.upload.dir:/var/www/ltl-league/uploads/prizes}")
    private String uploadDir;

    @Value("${ltl.upload.url-prefix:http://123.57.19.160/uploads/prizes}")
    private String uploadUrlPrefix;

    public PrizeController(PrizeService prizeService) {
        this.prizeService = prizeService;
    }

    @GetMapping
    public Result<List<PrizeVO>> listActivePrizes() {
        return Result.success(prizeService.listActivePrizes());
    }

    @GetMapping("/all")
    public Result<List<PrizeVO>> listAllPrizes() {
        return Result.success(prizeService.listAllPrizes());
    }

    @GetMapping("/{id}")
    public Result<PrizeVO> getPrize(@PathVariable Long id) {
        return Result.success(prizeService.getPrize(id));
    }

    @PostMapping
    public Result<PrizeVO> createPrize(@RequestBody CreatePrizeRequest request) {
        return Result.success(prizeService.createPrize(request));
    }

    @PostMapping("/{id}/update")
    public Result<PrizeVO> updatePrize(@PathVariable Long id, @RequestBody UpdatePrizeRequest request) {
        return Result.success(prizeService.updatePrize(id, request));
    }

    @PostMapping("/{id}/delete")
    public Result<Void> deletePrize(@PathVariable Long id) {
        prizeService.deletePrize(id);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择文件");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String filename = UUID.randomUUID().toString() + ext;

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());

            String url = uploadUrlPrefix + "/" + filename;

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            return Result.success(result);

        } catch (IOException e) {
            return Result.error("文件保存失败: " + e.getMessage());
        }
    }
}
