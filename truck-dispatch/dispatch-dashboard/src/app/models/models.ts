export type CargoType = 'STANDARD' | 'REFRIGERATED' | 'HAZARDOUS' | 'FRAGILE';
export type OrderPriority = 'NORMAL' | 'HIGH' | 'URGENT' | 'CRITICAL_DELIVERY';
export type OrderStatus = 'NEW' | 'VALID' | 'UNFEASIBLE' | 'ASSIGNED' | 'IN_PROGRESS' |
  'WAITING_RESOURCES' | 'WAITING_UNLOADING' | 'REPLANNED' | 'POSTPONED_UNTIL_MORNING' | 'COMPLETED';
export type TruckType = 'SMALL' | 'MEDIUM' | 'LARGE';
export type TruckStatus = 'AVAILABLE' | 'BUSY' | 'BREAKDOWN' | 'SERVICE';
export type RoadType = 'CITY' | 'LOCAL' | 'REGIONAL' | 'HIGHWAY';
export type FleetEventType = 'DELAY' | 'POSITION' | 'BREAKDOWN' | 'FUEL_LEVEL' | 'NEW_ORDER';
export type AlarmType = 'ESCALATION' | 'VEHICLE_STOPPED' | 'EMERGENCY_REPLACEMENT' | 'FUEL_LEAK' |
  'DOMINO_ESCALATION' | 'OVERLOADED_DRIVER' | 'OVERLOADED_TRUCK' | 'PROBLEMATIC_ROUTE' | 'SHIFT_WARNING' | 'IDLE_FLEET';

export interface DeliveryOrder {
  id: string;
  destination: string;
  weightKg: number;
  cargoType: CargoType;
  deliveryDeadlineMin: number;
  priority: OrderPriority;
  status: OrderStatus;
  routeId: string;
  assignedTruckId?: string;
  assignedDriverId?: string;
  delayMin?: number;
}

export interface Truck {
  id: string;
  type: TruckType;
  maxCapacityKg: number;
  status: TruckStatus;
  location: string;
  fuelPercent: number;
  hasRefrigerationUnit: boolean;
  hasAdrEquipment: boolean;
  distanceToOriginKm: number;
  daysSinceRefrigerationService: number;
}

export interface Driver {
  id: string;
  available: boolean;
  workingHoursToday: number;
  license: string;
  hasAdrLicense: boolean;
  fatigueLevel: number;
  yearsOfExperience: number;
  recentRouteIds: string[];
}

export interface Route {
  id: string;
  roadType: RoadType;
  distanceKm: number;
  estimatedTimeHours: number;
  maxCapacityKg: number;
  maxSpeedKmh: number;
  hasTunnel: boolean;
}

export interface Alarm {
  type: AlarmType;
  entityId: string;
  message: string;
  affectedCount: number;
}

export interface FleetEvent {
  type: FleetEventType;
  entityId: string;
  value: number;
  location: string;
  timestamp?: number;
}

export interface DispatchRequest {
  temperature: number;
  hour?: number;
  dayOfWeek?: number;
  orders: DeliveryOrder[];
  trucks: Truck[];
  drivers: Driver[];
  routes: Route[];
}

export interface DispatchResult {
  messages: string[];
  processedOrders: DeliveryOrder[];
  alarms: Alarm[];
}
