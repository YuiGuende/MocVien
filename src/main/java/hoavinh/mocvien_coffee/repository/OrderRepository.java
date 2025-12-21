package hoavinh.mocvien_coffee.repository;

import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.totalAmount),0) FROM Order o WHERE o.status='COMPLETED' AND o.createdAt BETWEEN :start AND :end")
    Double sumRevenueBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(OrderStatus status);

    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findByTableRefIdAndStatus(Long tableId, OrderStatus status);

    List<Order> findByTableRefIsNullAndStatus(OrderStatus status);
}

