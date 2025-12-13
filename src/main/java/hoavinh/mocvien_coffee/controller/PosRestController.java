package hoavinh.mocvien_coffee.controller;

import hoavinh.mocvien_coffee.dto.CafeTableDto;
import hoavinh.mocvien_coffee.dto.OrderRequest;
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
        var order = orderService.createOrder(request, user);
        return ResponseEntity.ok(java.util.Map.of(
                "orderId", order.getId(),
                "createdAt", order.getCreatedAt().toString()
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

