package com.ltl.league.admin.controller;

import com.ltl.league.common.Result;
import com.ltl.league.service.UploadCompressService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 上传资源维护：历史图片原地压缩。
 */
@RestController
@RequestMapping("/admin/uploads")
public class AdminUploadController {

    private final UploadCompressService uploadCompressService;

    public AdminUploadController(UploadCompressService uploadCompressService) {
        this.uploadCompressService = uploadCompressService;
    }

    /**
     * 扫描并原地压缩历史上传图片（奖品 / 队徽 / 战绩截图）。
     */
    @PostMapping("/compress-existing")
    public Result<Map<String, Object>> compressExisting() {
        return Result.success(uploadCompressService.compressExistingUploads());
    }
}
