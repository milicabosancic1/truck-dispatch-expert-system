package com.truckdispatch.truck_dispatch.repository;

import com.truckdispatch.truck_dispatch.entity.DriverEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverEntity, String> {}
