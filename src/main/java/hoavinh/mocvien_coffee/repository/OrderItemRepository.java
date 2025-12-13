package hoavinh.mocvien_coffee.repository;

import hoavinh.mocvien_coffee.model.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.product.name FROM OrderItem oi GROUP BY oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<String> findTopSellingProductNames(Pageable pageable);

    @Query("SELECT oi.product.category, SUM(oi.quantity) FROM OrderItem oi GROUP BY oi.product.category")
    List<Object[]> fetchCategoryMix();
}

