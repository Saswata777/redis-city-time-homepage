package org.example.services;

import org.example.model.Store;
import org.example.util.TimeUtil;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class HomeService {
    public List<Store> filterStores(List<Store> stores, String time) {

        return stores.stream()
                .filter(s -> TimeUtil.isStoreOpen(
                        s.getOpenTime(),
                        s.getCloseTime(),
                        time))
                .sorted((a,b) ->Integer.compare(b.getPriority(), a.getPriority()))
                .toList();
    }

}
