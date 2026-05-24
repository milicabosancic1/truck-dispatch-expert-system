package com.truckdispatch.truck_dispatch.dto;

import com.truckdispatch.truck_dispatch.model.Alarm;
import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DispatchResult {
    private List<String>        messages       = new ArrayList<>();
    private List<DeliveryOrder> processedOrders = new ArrayList<>();
    private List<Alarm>         alarms          = new ArrayList<>();
}
