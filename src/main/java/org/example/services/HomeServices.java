package org.example.services;

import org.example.model.Store;
import org.example.util.TimeUtil;

import java.util.List;

public class HomeServices {
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
