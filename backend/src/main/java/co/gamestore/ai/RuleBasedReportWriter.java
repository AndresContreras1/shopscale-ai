package co.gamestore.ai;

import co.gamestore.analytics.AnalyticsDtos.CategoryShare;
import co.gamestore.analytics.AnalyticsDtos.InventoryKpis;
import co.gamestore.analytics.AnalyticsDtos.ProductInsight;
import co.gamestore.analytics.AnalyticsDtos.SalesKpis;
import co.gamestore.analytics.AnalyticsDtos.StockHealth;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes reports with fixed templates from the computed facts. Used when no AI provider is configured
 * or when the provider fails, so the feature degrades gracefully instead of breaking.
 */
@Component
public class RuleBasedReportWriter {

    public String inventory(InventoryKpis kpis, List<ProductInsight> restock, List<ProductInsight> slow, String lang) {
        boolean es = "es".equals(lang);
        StringBuilder md = new StringBuilder();
        md.append(es ? "## Resumen ejecutivo\n" : "## Executive summary\n");
        md.append((es
                ? "- %d productos activos: %d agotados, %d en nivel crítico y %d con stock bajo.\n"
                : "- %d active products: %d out of stock, %d critical and %d low.\n")
                .formatted(kpis.activeProducts(), kpis.stockouts(), kpis.critical(), kpis.low()));
        md.append((es ? "- %d productos sin ventas y %d con sobrestock inmovilizan capital.\n"
                : "- %d products without sales and %d overstocked items are tying up cash.\n")
                .formatted(kpis.noSales(), kpis.overstock()));
        md.append((es ? "- Valor del inventario disponible: $%s.\n\n" : "- Available inventory value: $%s.\n\n")
                .formatted(kpis.inventoryValue()));

        md.append(es ? "## Reabastecer ahora\n" : "## Restock now\n");
        md.append(es ? "| SKU | Producto | Disponible | Días de cobertura | Pedido sugerido |\n"
                : "| SKU | Product | Available | Days of cover | Suggested order |\n");
        md.append("|---|---|---|---|---|\n");
        restock.stream().limit(8).forEach(p -> md.append("| %s | %s | %d | %s | %d |\n".formatted(p.sku(), p.name(),
                p.available(), p.daysOfCover() == null ? "0" : p.daysOfCover(), p.suggestedReorderQty())));

        md.append(es ? "\n## Inventario de baja rotación\n" : "\n## Slow-moving stock\n");
        slow.stream().limit(6).forEach(p -> md.append(p.health() == StockHealth.NO_SALES
                ? (es ? "- **%s** (%s): %d unidades sin ventas en 30 días. Considerar promoción o bundle.\n"
                      : "- **%s** (%s): %d units with no sales in 30 days. Consider a promotion or a bundle.\n")
                        .formatted(p.name(), p.sku(), p.available())
                : (es ? "- **%s** (%s): %.0f días de cobertura. Pausar compras.\n"
                      : "- **%s** (%s): %.0f days of cover. Pause purchasing.\n")
                        .formatted(p.name(), p.sku(), p.daysOfCover())));

        md.append(es ? "\n## Plan para los próximos 7 días\n" : "\n## Next 7 days\n");
        md.append(es ? "1. Emitir órdenes de compra para los productos críticos.\n"
                : "1. Issue purchase orders for the critical products.\n");
        md.append(es ? "2. Lanzar una campaña de descuento para el inventario sin rotación.\n"
                : "2. Run a discount campaign for the non-moving stock.\n");
        md.append(es ? "3. Revisar los puntos de reorden de los productos más vendidos.\n"
                : "3. Review reorder points of the best sellers.\n");
        return md.toString();
    }

    public String sales(SalesKpis kpis, List<CategoryShare> categories, List<ProductInsight> top, String lang) {
        boolean es = "es".equals(lang);
        StringBuilder md = new StringBuilder();
        md.append(es ? "## Resumen de ventas (30 días)\n" : "## Sales summary (30 days)\n");
        md.append((es ? "- Ingresos: $%s (%+.1f%% vs los 30 días anteriores).\n"
                : "- Revenue: $%s (%+.1f%% vs the previous 30 days).\n")
                .formatted(kpis.revenue30d(), kpis.revenueGrowthPct()));
        md.append((es ? "- Pedidos: %d · Ticket promedio: $%s.\n" : "- Orders: %d · Average order value: $%s.\n")
                .formatted(kpis.orders30d(), kpis.averageOrderValue()));
        md.append(es ? "\n## Categorías\n" : "\n## Categories\n");
        categories.forEach(c -> md.append("- %s: $%s (%d %s)\n".formatted(c.category(), c.revenue(), c.units(),
                es ? "unidades" : "units")));
        md.append(es ? "\n## Productos estrella\n" : "\n## Best sellers\n");
        top.forEach(p -> md.append("- **%s**: $%s, %d %s\n".formatted(p.name(), p.revenue30d(), p.unitsSold30d(),
                es ? "unidades" : "units")));
        md.append(es ? "\n## Recomendaciones\n- Asegurar stock de los productos estrella antes de campañas.\n"
                        + "- Usar los productos estrella como ancla en bundles con artículos de baja rotación.\n"
                : "\n## Recommendations\n- Secure stock of best sellers before campaigns.\n"
                        + "- Use best sellers as anchors in bundles with slow movers.\n");
        return md.toString();
    }

    public String productDescription(String name, String brand, String category, String lang) {
        return "es".equals(lang)
                ? "%s de %s: una opción confiable en %s, pensada para el uso diario. Calidad de marca, garantía y envío rápido."
                        .formatted(name, brand, category)
                : "%s by %s: a reliable choice in %s, built for everyday use. Brand quality, warranty and fast shipping."
                        .formatted(name, brand, category);
    }
}
