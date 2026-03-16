package com.suhas.stocktracker.config;

import com.suhas.stocktracker.service.DatabaseService;
import com.suhas.stocktracker.service.ScannerService;
import com.suhas.stocktracker.model.StrategyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupScanner {
    private static final Logger log = LoggerFactory.getLogger(StartupScanner.class);

    @Bean
    ApplicationRunner initialScannerRunner(DatabaseService databaseService, ScannerService scannerService) {
        return args -> {
            for (StrategyType strategyType : StrategyType.values()) {
                if (databaseService.hasScannerResults(strategyType)) {
                    continue;
                }
                log.info("No scanner results found for {}. Running initial watchlist scan.", strategyType.slug());
                scannerService.runScanner(strategyType);
            }
        };
    }
}
