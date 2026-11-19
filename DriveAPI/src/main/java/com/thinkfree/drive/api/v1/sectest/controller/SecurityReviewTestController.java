package com.thinkfree.drive.api.v1.sectest.controller;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 리소스 조회·업로드·검색 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sectest")
public class SecurityReviewTestController {

    private final DSLContext dsl;
    private final StorageService storageService;

    @GetMapping("/resources/{resourceSeq}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long resourceSeq) {
        Record r = dsl.fetchOne("select user_seq, resource_name from tb_resource where resource_seq = " + resourceSeq);
        String key = r.get("user_seq") + "/" + r.get("resource_name");
        FileData data = storageService.getFileData(key);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=" + r.get("resource_name"))
                .body(new InputStreamResource(data.content()));
    }

    @PostMapping("/upload")
    public void upload(@RequestParam String userSeq, @RequestParam MultipartFile file) {
        storageService.upload(file, userSeq + "/" + file.getOriginalFilename());
    }

    @GetMapping("/resources")
    public List<Map<String, Object>> search(@RequestParam String name, @RequestParam(defaultValue = "resource_name") String sort) {
        Result<Record> rows = dsl.fetch(
                "select resource_seq, resource_name from tb_resource where resource_name like '%" + name + "%' order by " + sort);
        return rows.intoMaps();
    }
}
