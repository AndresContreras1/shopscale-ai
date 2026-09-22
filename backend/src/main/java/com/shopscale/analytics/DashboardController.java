package com.shopscale.analytics;

import com.shopscale.analytics.AnalyticsDtos.Dashboard;
import com.shopscale.analytics.AnalyticsDtos.ProductInsight;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return analyticsService.dashboard();
    }

    @GetMapping("/insights/products")
    public List<ProductInsight> productInsights() {
        return analyticsService.productInsights();
    }
}
