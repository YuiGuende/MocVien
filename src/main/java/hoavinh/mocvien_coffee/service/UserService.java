package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.model.User;
import hoavinh.mocvien_coffee.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User createUser(String username, String password, String fullName, String role) {
        userRepository.findByUsername(username).ifPresent(user -> {
            throw new IllegalArgumentException("Username already exists");
        });
        String normalizedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        var user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(normalizedRole.toUpperCase())
                .build();
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void resetPassword(Long id, String newPassword) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}

