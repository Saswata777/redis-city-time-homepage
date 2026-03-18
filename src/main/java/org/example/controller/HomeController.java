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
    public List<Store> getHome(@RequestParam String city,
                               @RequestParam String time) {

        return homeService.getHomePage(city, time);
    }
}