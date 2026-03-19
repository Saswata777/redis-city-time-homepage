package org.example.repository;

import org.springframework.stereotype.Component;

import java.util.List;

/*
 * StoreRepository
 *
 * Single source of truth for the store catalogue.
 *
 * Why this exists (Phase 6):
 * DataSeeder needed the catalogue to seed Redis on startup.
 * RedisStoreService now also needs it to rebuild evicted city data.
 * Rather than duplicate the list, we extract it here so both classes
 * read from one place — adding a store is always a single edit.
 */
@Component
public class StoreRepository {

    public List<StoreRecord> findAll() {
        return List.of(
                new StoreRecord("BLR001", "Swiggy Instamart",   "bangalore", "00:00", "00:00", 10),
                new StoreRecord("BLR002", "Zomato",             "bangalore", "08:00", "23:00", 9),
                new StoreRecord("BLR003", "BigBasket",          "bangalore", "07:00", "22:00", 8),
                new StoreRecord("BLR004", "Blinkit",            "bangalore", "06:00", "00:00", 7),
                new StoreRecord("BLR005", "MTR Restaurant",     "bangalore", "07:30", "22:30", 6),
                new StoreRecord("BLR006", "Burger King",        "bangalore", "10:00", "02:00", 5),
                new StoreRecord("BLR007", "24x7 Medical Store", "bangalore", "00:00", "00:00", 4),

                new StoreRecord("DEL001", "Zepto",              "delhi", "00:00", "00:00", 10),
                new StoreRecord("DEL002", "Zomato",             "delhi", "09:00", "23:30", 9),
                new StoreRecord("DEL003", "DMart",              "delhi", "08:00", "22:00", 8),
                new StoreRecord("DEL004", "Haldiram's",         "delhi", "08:00", "23:00", 7),
                new StoreRecord("DEL005", "Mcdonald's",         "delhi", "09:00", "03:00", 6),
                new StoreRecord("DEL006", "Night Pharmacy",     "delhi", "21:00", "09:00", 5),

                new StoreRecord("MUM001", "Blinkit",            "mumbai", "00:00", "00:00", 10),
                new StoreRecord("MUM002", "Swiggy",             "mumbai", "08:00", "01:00", 9),
                new StoreRecord("MUM003", "Reliance Smart",     "mumbai", "09:00", "21:00", 8),
                new StoreRecord("MUM004", "Vada Pav Corner",    "mumbai", "07:00", "23:00", 6),
                new StoreRecord("MUM005", "Cafe Coffee Day",    "mumbai", "07:00", "22:00", 5),

                new StoreRecord("CHN001", "Aavin Milk",         "chennai", "05:00", "09:00", 9),
                new StoreRecord("CHN002", "Swiggy",             "chennai", "09:00", "23:00", 8),
                new StoreRecord("CHN003", "Saravana Stores",    "chennai", "10:00", "21:00", 7),
                new StoreRecord("CHN004", "Apollo Pharmacy",    "chennai", "00:00", "00:00", 10),
                new StoreRecord("CHN005", "Murugan Idli Shop",  "chennai", "06:00", "22:00", 6),

                new StoreRecord("HYD001", "Zepto",              "hyderabad", "00:00", "00:00", 10),
                new StoreRecord("HYD002", "Paradise Biryani",   "hyderabad", "11:00", "23:30", 9),
                new StoreRecord("HYD003", "Big Bazaar",         "hyderabad", "09:00", "21:30", 8),
                new StoreRecord("HYD004", "Zomato",             "hyderabad", "09:00", "00:00", 7),
                new StoreRecord("HYD005", "Night Dhaba",        "hyderabad", "20:00", "06:00", 5)
        );
    }

    public List<StoreRecord> findByCity(String city) {
        return findAll().stream()
                .filter(s -> s.city().equals(city))
                .toList();
    }

    public record StoreRecord(
            String id, String name, String city,
            String openTime, String closeTime, int priority
    ) {}
}