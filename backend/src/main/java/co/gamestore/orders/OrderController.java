package co.gamestore.orders;

import co.gamestore.common.ConflictRetry;
import co.gamestore.common.PageResponse;
import co.gamestore.orders.dto.OrderDtos.CheckoutRequest;
import co.gamestore.orders.dto.OrderDtos.OrderResponse;
import co.gamestore.orders.dto.OrderDtos.StatusChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ConflictRetry conflictRetry;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request, Authentication auth) {
        return conflictRetry.execute(() -> orderService.checkout(auth.getName(), request));
    }

    @PostMapping("/{orderNumber}/pay")
    public OrderResponse pay(@PathVariable String orderNumber, Authentication auth) {
        return conflictRetry.execute(() -> orderService.pay(orderNumber, auth.getName(), isAdmin(auth)));
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(@PathVariable String orderNumber, Authentication auth) {
        return conflictRetry.execute(() -> orderService.cancel(orderNumber, auth.getName(), isAdmin(auth)));
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse find(@PathVariable String orderNumber, Authentication auth) {
        return orderService.find(orderNumber, auth.getName(), isAdmin(auth));
    }

    @GetMapping("/mine")
    public PageResponse<OrderResponse> mine(Authentication auth,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return orderService.mine(auth.getName(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<OrderResponse> all(@RequestParam(required = false) OrderStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return orderService.all(status, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @PutMapping("/{orderNumber}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse changeStatus(@PathVariable String orderNumber, @Valid @RequestBody StatusChangeRequest request) {
        return conflictRetry.execute(() -> orderService.changeStatus(orderNumber, request.status()));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
