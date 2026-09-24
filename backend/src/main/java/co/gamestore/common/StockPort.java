package co.gamestore.common;

import java.util.Collection;
import java.util.Map;

/**
 * What the catalog is allowed to know about stock.
 *
 * <p>The catalog shows availability and provisions stock for a new product, but it must not depend on
 * the inventory module: inventory already depends on the catalog because stock belongs to a product,
 * and two modules pointing at each other is a cycle. The dependency is inverted here instead: the
 * catalog talks to this interface in the shared kernel and the inventory module implements it.
 */
public interface StockPort {

    /** Available units (on hand minus reserved) for each product that has an inventory item. */
    Map<Long, Integer> availableFor(Collection<Long> productIds);

    /** Creates the inventory item for a product that has just been added to the catalog. */
    void provision(Long productId, int onHand, int reorderPoint);
}
