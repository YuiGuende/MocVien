package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAvailableProducts(String category) {
        return searchProducts(category, null);
    }

    public List<Product> searchProducts(String category, String search) {
        List<Product> base = (category == null || category.isBlank() || category.equalsIgnoreCase("all"))
                ? productRepository.findAllAvailable()
                : productRepository.findByCategoryIgnoreCaseAndAvailableTrueOrderByNameAsc(category);

        if (search == null || search.isBlank()) {
            return base;
        }
        String keyword = search.toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(product -> product.getName().toLowerCase(Locale.ROOT).contains(keyword))
                .collect(Collectors.toList());
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    public Product getById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
}

