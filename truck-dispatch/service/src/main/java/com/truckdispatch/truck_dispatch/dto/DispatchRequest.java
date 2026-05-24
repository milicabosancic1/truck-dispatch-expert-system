package com.truckdispatch.truck_dispatch.dto;

import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import com.truckdispatch.truck_dispatch.model.Driver;
import com.truckdispatch.truck_dispatch.model.Route;
import com.truckdispatch.truck_dispatch.model.Truck;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchRequest {
    private double temperature;
    private Integer hour;        // null = auto-detect from system time
    private Integer dayOfWeek;   // null = auto-detect; 1=MON, 7=SUN

    private List<DeliveryOrder> orders  = new ArrayList<>();
    private List<Truck>         trucks  = new ArrayList<>();
    private List<Driver>        drivers = new ArrayList<>();
    private List<Route>         routes  = new ArrayList<>();
}
