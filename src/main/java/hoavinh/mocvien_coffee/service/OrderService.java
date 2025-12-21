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
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public Order createOrUpdatePendingOrder(OrderRequest request, User user) {
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

        // Tìm pending order hiện có cho bàn này
        List<Order> existingPendingOrders;
        if (table != null) {
            existingPendingOrders = orderRepository.findByTableRefIdAndStatus(table.getId(), OrderStatus.PENDING);
        } else {
            existingPendingOrders = orderRepository.findByTableRefIsNullAndStatus(OrderStatus.PENDING);
        }

        Order order;
        if (!existingPendingOrders.isEmpty()) {
            // Cập nhật pending order hiện có
            order = existingPendingOrders.get(0);
            order.getItems().clear(); // Xóa items cũ
        } else {
            // Tạo pending order mới
            order = Order.builder()
                    .tableNumber(tableNumber)
                    .createdAt(LocalDateTime.now())
                    .status(OrderStatus.PENDING)
                    .createdBy(user)
                    .build();
            order.setTableRef(table);
        }

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
        order.setTotalAmount(total);

        return orderRepository.save(order);
    }

    public List<Order> getPendingOrdersForTable(Long tableId) {
        if (tableId != null) {
            return orderRepository.findByTableRefIdAndStatus(tableId, OrderStatus.PENDING);
        } else {
            return orderRepository.findByTableRefIsNullAndStatus(OrderStatus.PENDING);
        }
    }

    @Transactional
    public Order completePendingOrder(Long orderId, OrderRequest request, User user) {
        Order pendingOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pending order not found"));
        
        if (pendingOrder.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not pending");
        }

        // Cập nhật items nếu có thay đổi
        if (request.items() != null && !request.items().isEmpty()) {
            pendingOrder.getItems().clear();
            for (var item : request.items()) {
                Product product = productRepository.findById(item.productId())
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                double unitPrice = item.price() != null ? item.price() : product.getPrice();
                var orderItem = OrderItem.builder()
                        .order(pendingOrder)
                        .product(product)
                        .quantity(item.quantity())
                        .price(unitPrice)
                        .note(item.note())
                        .build();
                pendingOrder.getItems().add(orderItem);
            }
        }

        // Cập nhật thông tin thanh toán và surcharge
        pendingOrder.setStatus(OrderStatus.COMPLETED);
        pendingOrder.setCustomerCash(request.customerCash());
        pendingOrder.setChangeAmount(request.changeAmount());
        pendingOrder.setTotalAmount(request.totalAmount());
        if (request.surchargePercent() != null) {
            pendingOrder.setSurchargePercent(request.surchargePercent());
        }
        if (request.surchargeAmount() != null) {
            pendingOrder.setSurchargeAmount(request.surchargeAmount());
        }
        if (request.surchargeName() != null) {
            pendingOrder.setSurchargeName(request.surchargeName());
        }

        Order saved = orderRepository.save(pendingOrder);
        
        // Release table nếu có
        if (pendingOrder.getTableRef() != null) {
            tableService.release(pendingOrder.getTableRef().getId());
        }
        
        return saved;
    }
}

