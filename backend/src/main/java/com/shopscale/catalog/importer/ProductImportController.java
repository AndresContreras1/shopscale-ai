package com.shopscale.catalog.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/import")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService importService;

    /** Accepts the file and answers immediately; poll the returned job for progress. */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportJob start(@RequestParam("file") MultipartFile file) {
        return importService.start(file);
    }

    @GetMapping("/{jobId}")
    public ImportJob status(@PathVariable String jobId) {
        return importService.status(jobId);
    }
}
