package com.kxh.aiagent.controller;


import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    // 项目根目录下的 tmp 目录
    private final Path rootLocation = Paths.get("tmp/markdown").toAbsolutePath().normalize();

    // 获取文件列表
    @GetMapping
    public ResponseEntity<List<String>> listFiles() throws IOException {
        // 确保目录存在
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }

        List<String> fileNames = Files.walk(rootLocation, 1)
                .filter(path -> !path.equals(rootLocation))
                .map(path -> MvcUriComponentsBuilder.fromMethodName(
                                FileController.class, "downloadFile", path.getFileName().toString())
                        .build().toString())
                .collect(Collectors.toList());

        return ResponseEntity.ok(fileNames);
    }

    // 下载文件
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path file = rootLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                // 确定内容类型
                String contentType = Files.probeContentType(file);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 上传文件
    @PostMapping
    public ResponseEntity<String> uploadFile(@RequestBody byte[] fileContent,
                                             @RequestParam String filename) {
        try {
            Path targetLocation = rootLocation.resolve(filename);
            Files.write(targetLocation, fileContent);
            return ResponseEntity.ok("文件上传成功: " + filename);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("无法上传文件: " + e.getMessage());
        }
    }
}