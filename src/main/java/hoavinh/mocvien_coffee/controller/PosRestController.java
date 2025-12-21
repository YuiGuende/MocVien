package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.dto.CafeTableDto;
import hoavinh.mocvien_coffee.dto.OrderRequest;
import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.model.User;
import hoavinh.mocvien_coffee.repository.UserRepository;
import hoavinh.mocvien_coffee.service.OrderService;
import hoavinh.mocvien_coffee.service.ProductService;
import hoavinh.mocvien_coffee.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pos")
public class PosRestController {

    private final ProductService productService;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final TableService tableService;

    public PosRestController(ProductService productService,
                             OrderService orderService,
                             UserRepository userRepository,
                             TableService tableService) {
        this.productService = productService;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.tableService = tableService;
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String search) {
        return productService.searchProducts(category, search);
    }

    @GetMapping("/tables")
    public List<CafeTableDto> tables() {
        return tableService.findActive().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/tables/{id}/occupy")
    public CafeTableDto occupy(@PathVariable Long id) {
        return toDto(tableService.occupy(id));
    }

    @PostMapping("/tables/{id}/release")
    public CafeTableDto release(@PathVariable Long id) {
        return toDto(tableService.release(id));
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderRequest request,
                                         Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Kiểm tra xem có pending order không
        List<Order> pendingOrders = orderService.getPendingOrdersForTable(request.tableId());
        if (!pendingOrders.isEmpty()) {
            // Cập nhật pending order thành COMPLETED
            Order pendingOrder = pendingOrders.get(0);
            var order = orderService.completePendingOrder(pendingOrder.getId(), request, user);
            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "createdAt", order.getCreatedAt().toString()
            ));
        } else {
            // Tạo order mới
            var order = orderService.createOrder(request, user);
            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "createdAt", order.getCreatedAt().toString()
            ));
        }
    }

    @PostMapping("/orders/pending")
    public ResponseEntity<?> savePendingOrder(@Valid @RequestBody OrderRequest request,
                                              Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        var order = orderService.createOrUpdatePendingOrder(request, user);
        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "createdAt", order.getCreatedAt().toString()
        ));
    }

    @GetMapping("/orders/pending")
    public ResponseEntity<?> getPendingOrders(@RequestParam(required = false) Long tableId) {
        List<Order> pendingOrders = orderService.getPendingOrdersForTable(tableId);
        if (pendingOrders.isEmpty()) {
            return ResponseEntity.ok(Map.of("items", List.of()));
        }
        
        // Chỉ lấy order đầu tiên (nên chỉ có 1 pending order cho mỗi bàn)
        Order order = pendingOrders.get(0);
        
        // Convert order items to cart format
        List<Map<String, Object>> items = order.getItems().stream()
                .map(item -> Map.<String, Object>of(
                        "id", item.getProduct().getId(),
                        "name", item.getProduct().getName(),
                        "category", item.getProduct().getCategory(),
                        "unitPrice", item.getPrice(),
                        "quantity", item.getQuantity(),
                        "note", item.getNote() != null ? item.getNote() : "",
                        "notified", true, // Đã báo chế biến nên là true
                        "priceOverride", null
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
                "items", items,
                "surchargePercent", order.getSurchargePercent() != null ? order.getSurchargePercent() : 0,
                "surchargeName", order.getSurchargeName() != null ? order.getSurchargeName() : "",
                "orderId", order.getId()
        ));
    }

    private CafeTableDto toDto(hoavinh.mocvien_coffee.model.CafeTable table) {
        return new CafeTableDto(
                table.getId(),
                table.getName(),
                table.getStatus().name(),
                table.isActive(),
                table.getOccupiedAt()
        );
    }
}

