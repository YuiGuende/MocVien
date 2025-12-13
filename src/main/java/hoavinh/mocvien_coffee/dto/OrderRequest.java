package hoavinh.mocvien_coffee.dto;

import java.util.List;

public record OrderRequest(Long tableId,
                           String tableNumber,
                           Double totalAmount,
                           Double surchargePercent,
                           Double surchargeAmount,
                           String surchargeName,
                           Double customerCash,
                           Double changeAmount,
                           List<OrderItemRequest> items) {
}

