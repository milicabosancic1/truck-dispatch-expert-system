package com.truckdispatch.truck_dispatch.repository;

import com.truckdispatch.truck_dispatch.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<RouteEntity, String> {}
