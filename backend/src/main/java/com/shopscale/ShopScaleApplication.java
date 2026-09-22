package com.shopscale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShopScaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopScaleApplication.class, args);
    }
}
