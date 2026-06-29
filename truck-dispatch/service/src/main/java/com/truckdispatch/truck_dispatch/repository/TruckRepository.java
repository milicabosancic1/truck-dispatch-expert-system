package com.truckdispatch.truck_dispatch.repository;

import com.truckdispatch.truck_dispatch.entity.TruckEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<TruckEntity, String> {}
