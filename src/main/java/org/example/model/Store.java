package org.example.model;

public class Store {
    private String id;
    private String name;
    private String city;
    private String openTime;
    private String closeTime;
    private int priority;


    public Store() {
    }

    public Store(String id, String name, String city,
                 String openTime, String closeTime, int priority) {

        this.id = id;
        this.name = name;
        this.city = city;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.priority = priority;
    }

    // Getters & Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
