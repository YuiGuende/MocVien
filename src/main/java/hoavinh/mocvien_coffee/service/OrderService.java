package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.dto.OrderRequest;
import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.model.OrderItem;
import hoavinh.mocvien_coffee.model.OrderStatus;
import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.model.User;
import hoavinh.mocvien_coffee.repository.OrderRepository;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TableService tableService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        TableService tableService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.tableService = tableService;
    }

    @Transactional
    public Order createOrder(OrderRequest request, User user) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain items");
        }

        CafeTable table = null;
        String tableNumber = (request.tableNumber() == null || request.tableNumber().isBlank())
                ? "Takeout"
                : request.tableNumber();
        if (request.tableId() != null) {
            table = tableService.getById(request.tableId());
            tableNumber = table.getName();
        }

        var order = Order.builder()
                .tableNumber(tableNumber)
                .createdAt(LocalDateTime.now())
                .status(OrderStatus.COMPLETED)
                .createdBy(user)
                .build();
        order.setTableRef(table);

        double derivedTotal = 0;
        for (var item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            double unitPrice = item.price() != null ? item.price() : product.getPrice();
            derivedTotal += unitPrice * item.quantity();
            var orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(item.quantity())
                    .price(unitPrice)
                    .note(item.note())
                    .build();
            order.getItems().add(orderItem);
        }

        double surchargePercent = request.surchargePercent() != null ? request.surchargePercent() : 0d;
        double surchargeAmount = request.surchargeAmount() != null
                ? request.surchargeAmount()
                : derivedTotal * surchargePercent / 100d;
        double total = request.totalAmount() != null ? request.totalAmount() : derivedTotal + surchargeAmount;

        order.setSurchargeName(request.surchargeName());
        order.setSurchargePercent(surchargePercent);
        order.setSurchargeAmount(surchargeAmount);
        order.setCustomerCash(request.customerCash());
        order.setChangeAmount(request.changeAmount());
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        if (table != null) {
            tableService.release(table.getId());
        }
        return saved;
    }
}

