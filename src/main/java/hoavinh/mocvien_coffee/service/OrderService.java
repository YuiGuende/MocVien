package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.dto.OrderRequest;
import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.Customer;
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
    private final CustomerService customerService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        TableService tableService,
                        CustomerService customerService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.tableService = tableService;
        this.customerService = customerService;
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

        Customer customer = null;
        // Xử lý customer: tìm hoặc tạo mới
        if (request.customerPhoneNumber() != null && !request.customerPhoneNumber().isBlank()) {
            // Tìm customer theo số điện thoại
            var existingCustomer = customerService.findByPhoneNumber(request.customerPhoneNumber());
            if (existingCustomer.isPresent()) {
                customer = existingCustomer.get();
                // Cập nhật tên nếu có thay đổi
                boolean nameChanged = false;
                if (request.customerName() != null && !request.customerName().isBlank() 
                    && !request.customerName().equals(customer.getName())) {
                    customer.setName(request.customerName());
                    nameChanged = true;
                }
                // Đảm bảo points không null
                if (customer.getPoints() == null) {
                    customer.setPoints(0);
                }
                // Save nếu có thay đổi tên
                if (nameChanged) {
                    customer = customerService.save(customer);
                }
            } else {
                // Tạo customer mới
                if (request.customerName() == null || request.customerName().isBlank()) {
                    throw new IllegalArgumentException("Customer name is required when creating new customer");
                }
                customer = Customer.builder()
                        .name(request.customerName())
                        .phoneNumber(request.customerPhoneNumber())
                        .points(0)
                        .build();
                // Save customer mới trước
                customer = customerService.save(customer);
            }
        } else if (request.customerId() != null) {
            customer = customerService.getById(request.customerId());
            // Đảm bảo points không null
            if (customer.getPoints() == null) {
                customer.setPoints(0);
            }
        }

        var order = Order.builder()
                .tableNumber(tableNumber)
                .createdAt(LocalDateTime.now())
                .status(OrderStatus.COMPLETED)
                .createdBy(user)
                .customer(customer)
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
        
        // Tính điểm tích lũy: 1 điểm = 1000 VNĐ (hoặc có thể điều chỉnh)
        if (customer != null && total > 0) {
            int currentPoints = customer.getPoints() != null ? customer.getPoints() : 0;
            int pointsEarned = (int) (total / 1000); // 1000 VNĐ = 1 điểm
            customer.setPoints(currentPoints + pointsEarned);
            customerService.save(customer);
        }
        
        if (table != null) {
            tableService.release(table.getId());
        }
        return saved;
    }
}

