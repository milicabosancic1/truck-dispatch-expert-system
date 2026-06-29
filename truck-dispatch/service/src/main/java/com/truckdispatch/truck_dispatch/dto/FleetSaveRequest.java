package com.truckdispatch.truck_dispatch.dto;

import com.truckdispatch.truck_dispatch.model.Driver;
import com.truckdispatch.truck_dispatch.model.Route;
import com.truckdispatch.truck_dispatch.model.Truck;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FleetSaveRequest {
    private List<Truck>  trucks  = new ArrayList<>();
    private List<Driver> drivers = new ArrayList<>();
    private List<Route>  routes  = new ArrayList<>();
}
