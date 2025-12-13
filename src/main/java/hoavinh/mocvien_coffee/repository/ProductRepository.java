package hoavinh.mocvien_coffee.repository;

import hoavinh.mocvien_coffee.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryIgnoreCaseAndAvailableTrueOrderByNameAsc(String category);

    @Query("SELECT p FROM Product p WHERE p.available = true ORDER BY p.name ASC")
    List<Product> findAllAvailable();
}

