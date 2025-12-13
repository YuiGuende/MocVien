package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.model.CafeSettings;
import hoavinh.mocvien_coffee.repository.CafeSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final CafeSettingsRepository settingsRepository;

    public SettingsService(CafeSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public CafeSettings getSettings() {
        return settingsRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> settingsRepository.save(CafeSettings.builder()
                        .shopName("Cà Phê Mộc Viên")
                        .phone("0900 000 000")
                        .address("123 Phan Xich Long, Q.Phú Nhuận")
                        .qrImageUrl(null)
                        .surchargeName("Phí phục vụ")
                        .surchargePercent(0.0)
                        .build()));
    }

    public CafeSettings save(CafeSettings payload) {
        CafeSettings existing = getSettings();
        existing.setShopName(payload.getShopName());
        existing.setPhone(payload.getPhone());
        existing.setAddress(payload.getAddress());
        existing.setQrImageUrl(payload.getQrImageUrl());
        existing.setSurchargeName(payload.getSurchargeName());
        existing.setSurchargePercent(payload.getSurchargePercent());
        return settingsRepository.save(existing);
    }
}

