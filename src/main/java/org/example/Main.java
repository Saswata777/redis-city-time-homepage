package org.example;

import org.example.model.Store;
import org.example.services.HomeServices;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Store s1 = new Store("1","Shop1","blr","09:00","21:00",5);
        Store s2 = new Store("2","Shop2","blr","22:00","04:00",8);
        Store s3 = new Store("3","Shop3","blr","00:00","00:00",3);

        List<Store> stores = List.of(s1,s2,s3);

        HomeServices service = new HomeServices();

        List<Store> result =
                service.filterStores(stores,"02:00");

        result.forEach(s ->
                System.out.println(s.getName()));
    }
}