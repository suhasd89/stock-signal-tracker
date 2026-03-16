package com.suhas.stocktracker.controller;

import com.suhas.stocktracker.model.ScannerRunResponse;
import com.suhas.stocktracker.model.StrategyType;
import com.suhas.stocktracker.service.ScannerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanner")
public class ScannerController {
    private final ScannerService scannerService;

    public ScannerController(ScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @PostMapping("/run")
    public ScannerRunResponse run(@RequestParam(defaultValue = "sma") String strategy) {
        return scannerService.runScanner(StrategyType.fromSlug(strategy));
    }
}
