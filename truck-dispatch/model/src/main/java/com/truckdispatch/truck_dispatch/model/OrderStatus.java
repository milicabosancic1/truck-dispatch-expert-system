package com.truckdispatch.truck_dispatch.model;

public enum OrderStatus {
    NEW,
    VALID,
    UNFEASIBLE,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_RESOURCES,
    WAITING_UNLOADING,
    REPLANNED,
    POSTPONED_UNTIL_MORNING,
    COMPLETED
}