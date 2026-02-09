package org.example.controller;

import org.example.model.Store;
import org.example.services.HomeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public List<Store> getHome(@RequestParam String city, @RequestParam String time) {
        // TEMP data (Redis later)
        List<Store> stores = List.of(
                new Store("1", "Shop1", city, "09:00", "21:00", 5),
                new Store("2", "Shop2", city, "22:00", "04:00", 8),
                new Store("3", "Shop3", city, "00:00", "00:00", 3)
        );

        return homeService.filterStores(stores, time);
    }
}
