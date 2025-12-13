package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.dto.ChartDataPoint;
import hoavinh.mocvien_coffee.dto.DashboardStats;
import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.repository.OrderItemRepository;
import hoavinh.mocvien_coffee.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public DashboardService(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public DashboardStats buildTodayStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        Double revenue = orderRepository.sumRevenueBetween(start, end);
        long orders = orderRepository.countByCreatedAtBetween(start, end);
        var top = orderItemRepository.findTopSellingProductNames(PageRequest.of(0, 1));
        String topItem = top.isEmpty() ? "N/A" : top.getFirst();
        return new DashboardStats(revenue == null ? 0d : revenue, orders, topItem);
    }

    public List<ChartDataPoint> revenueSeries(String range) {
        RangeWindow window = determineWindow(range);
        List<Order> orders = orderRepository.findByCreatedAtBetween(window.start(), window.end());
        if (range.equalsIgnoreCase("today")) {
            Map<Integer, Double> hourly = new LinkedHashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                hourly.put(hour, 0d);
            }
            orders.forEach(order -> {
                int hour = order.getCreatedAt().getHour();
                hourly.merge(hour, order.getTotalAmount(), Double::sum);
            });
            return hourly.entrySet().stream()
                    .map(entry -> new ChartDataPoint(String.format("%02d:00", entry.getKey()), round(entry.getValue())))
                    .toList();
        }
        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        long days = ChronoUnit.DAYS.between(window.start().toLocalDate(), window.end().toLocalDate());
        for (int i = 0; i < days; i++) {
            LocalDate date = window.start().toLocalDate().plusDays(i);
            daily.put(date, 0d);
        }
        orders.forEach(order -> {
            LocalDate key = order.getCreatedAt().toLocalDate();
            daily.merge(key, order.getTotalAmount(), Double::sum);
        });
        return daily.entrySet().stream()
                .map(entry -> new ChartDataPoint(formatLabel(entry.getKey(), range), round(entry.getValue())))
                .toList();
    }

    public List<ChartDataPoint> productMix() {
        var rows = orderItemRepository.fetchCategoryMix();
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        return rows.stream()
                .map(row -> new ChartDataPoint((String) row[0], round(((Number) row[1]).doubleValue())))
                .collect(Collectors.toList());
    }

    private RangeWindow determineWindow(String range) {
        LocalDate today = LocalDate.now();
        if ("week".equalsIgnoreCase(range)) {
            LocalDate start = today.minusDays(6);
            return new RangeWindow(start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        } else if ("month".equalsIgnoreCase(range)) {
            LocalDate start = today.minusDays(29);
            return new RangeWindow(start.atStartOfDay(), today.plusDays(1).atStartOfDay());
        }
        return new RangeWindow(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatLabel(LocalDate date, String range) {
        if ("month".equalsIgnoreCase(range)) {
            return date.format(DateTimeFormatter.ofPattern("MM/dd"));
        }
        return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault());
    }

    private record RangeWindow(LocalDateTime start, LocalDateTime end) {
    }
}

