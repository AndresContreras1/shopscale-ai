package co.gamestore;

import co.gamestore.common.BrandProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BrandProperties.class)
/*
 * Every package directly under co.gamestore is an application module. Modules talk to each other
 * through the types in their own top-level package, never through a nested package, and cycles are
 * rejected by ModularityTests. "common" is the shared kernel every module may use.
 */
@Modulithic(systemName = "Game Store", sharedModules = "common")
public class GameStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameStoreApplication.class, args);
    }
}
