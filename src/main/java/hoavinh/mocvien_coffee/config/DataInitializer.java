package hoavinh.mocvien_coffee.config;

import hoavinh.mocvien_coffee.model.CafeSettings;
import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.Product;
import hoavinh.mocvien_coffee.model.TableStatus;
import hoavinh.mocvien_coffee.model.User;
import hoavinh.mocvien_coffee.repository.CafeSettingsRepository;
import hoavinh.mocvien_coffee.repository.CafeTableRepository;
import hoavinh.mocvien_coffee.repository.ProductRepository;
import hoavinh.mocvien_coffee.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CafeTableRepository tableRepository;
    private final CafeSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String staffUsername;
    private final String staffPassword;

    public DataInitializer(UserRepository userRepository,
                           ProductRepository productRepository,
                           CafeTableRepository tableRepository,
                           CafeSettingsRepository settingsRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.bootstrap.admin-username}") String adminUsername,
                           @Value("${app.bootstrap.admin-password}") String adminPassword,
                           @Value("${app.bootstrap.staff-username}") String staffUsername,
                           @Value("${app.bootstrap.staff-password}") String staffPassword) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.tableRepository = tableRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.staffUsername = staffUsername;
        this.staffPassword = staffPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            userRepository.save(User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName("Cafe Administrator")
                    .role("ROLE_ADMIN")
                    .build());
        }

        if (userRepository.findByUsername(staffUsername).isEmpty()) {
            userRepository.save(User.builder()
                    .username(staffUsername)
                    .password(passwordEncoder.encode(staffPassword))
                    .fullName("Cafe Staff")
                    .role("ROLE_STAFF")
                    .build());
        }

        if (productRepository.count() == 0) {
            List<Product> seedProducts = List.of(
                    Product.builder().name("Espresso").category("Coffee").price(2.5).imageUrl("/images/espresso.png").available(true).build(),
                    Product.builder().name("Latte").category("Coffee").price(3.5).imageUrl("/images/latte.png").available(true).build(),
                    Product.builder().name("Cappuccino").category("Coffee").price(3.2).imageUrl("/images/cappuccino.png").available(true).build(),
                    Product.builder().name("Vietnamese Coffee").category("Coffee").price(2.8).imageUrl("/images/vncoffee.png").available(true).build(),
                    Product.builder().name("Matcha Latte").category("Tea").price(3.1).imageUrl("/images/matcha.png").available(true).build(),
                    Product.builder().name("Jasmine Tea").category("Tea").price(2.0).imageUrl("/images/jasmine.png").available(true).build(),
                    Product.builder().name("Thai Milk Tea").category("Tea").price(2.9).imageUrl("/images/thai.png").available(true).build(),
                    Product.builder().name("Mango Smoothie").category("Smoothie").price(3.4).imageUrl("/images/mango.png").available(true).build(),
                    Product.builder().name("Avocado Smoothie").category("Smoothie").price(3.8).imageUrl("/images/avocado.png").available(true).build(),
                    Product.builder().name("Berry Smoothie").category("Smoothie").price(3.6).imageUrl("/images/berry.png").available(true).build()
            );
            productRepository.saveAll(seedProducts);
        }

        if (tableRepository.count() == 0) {
            for (int i = 1; i <= 30; i++) {
                tableRepository.save(CafeTable.builder()
                        .name("B." + i)
                        .status(TableStatus.AVAILABLE)
                        .active(true)
                        .build());
            }
        }

        if (settingsRepository.count() == 0) {
            settingsRepository.save(CafeSettings.builder()
                    .shopName("Cà Phê Mộc Viên")
                    .phone("0900 000 000")
                    .address("123 Phan Xích Long, Phú Nhuận")
                    .qrImageUrl(null)
                    .surchargeName("Phí phục vụ")
                    .surchargePercent(0.0)
                    .build());
        }
    }
}

