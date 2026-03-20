package org.example.model;
/*
-----------------------------------------
              Store (Class)
-----------------------------------------
- id         : String
- name       : String
- city       : String
- openTime   : String
- closeTime  : String
- priority   : int
-----------------------------------------
*/


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    private String id;
    private String name;
    private String city;
    private String openTime;
    private String closeTime;
    private int priority;
}
