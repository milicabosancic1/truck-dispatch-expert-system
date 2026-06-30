import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DispatchResult, OrderStatus, Alarm, AlarmType } from '../../models/models';

interface BcDiagnosis {
  orderId: string;
  causes: string[];
  status: OrderStatus;
}

@Component({
  selector: 'app-results-panel',
  imports: [CommonModule],
  templateUrl: './results-panel.component.html',
  styleUrl: './results-panel.component.css'
})
export class ResultsPanelComponent {
  @Input() result!: DispatchResult;
  @Input() showDemo = false;

  count(status: OrderStatus): number {
    return this.result.processedOrders.filter(o => o.status === status).length;
  }

  get assignedCount():  number { return this.count('ASSIGNED'); }
  get waitingCount():   number {
    return this.count('WAITING_RESOURCES') + this.count('WAITING_UNLOADING') + this.count('POSTPONED_UNTIL_MORNING');
  }
  get issueCount():     number { return this.count('UNFEASIBLE') + this.result.alarms.length; }
  get replannedCount(): number { return this.count('REPLANNED'); }

  // Canonical chain order: leaf causes (most specific) → intermediate connectors → OrderUnassigned (always last in template)
  private readonly CAUSE_ORDER = [
    'CargoOverweight', 'WinterReducesCapacity', 'NoFrigoTruck', 'AllTrucksBusy',
    'InsufficientFuelForRoute', 'RefrigerationServiceOverdue', 'RouteCapacityExceeded',
    'AllDriversOverHours', 'AllDriversFatigued', 'NoAdrLicense',
    'DriverWouldExceedWorkingHours', 'NightModeRestriction', 'WeekendRestriction',
    'HazardousCityRoute', 'RouteTooSlowForDeadline',
    'InsufficientCapacity', 'NoTruckAvailable', 'NoDriverAvailable',
  ];

  get bcDiagnoses(): BcDiagnosis[] {
    const map = new Map<string, string[]>();
    for (const msg of this.result.messages) {
      const m = msg.match(/^Order (\S+) DIAGNOSIS cause: (\S+)$/);
      if (m) {
        const [, orderId, cause] = m;
        if (!map.has(orderId)) map.set(orderId, []);
        map.get(orderId)!.push(cause);
      }
    }
    return Array.from(map.entries()).map(([orderId, causes]) => {
      const sorted = [...causes].sort((a, b) => {
        const ia = this.CAUSE_ORDER.indexOf(a);
        const ib = this.CAUSE_ORDER.indexOf(b);
        return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
      });
      const order = this.result.processedOrders.find(o => o.id === orderId);
      return { orderId, causes: sorted, status: order?.status ?? 'UNFEASIBLE' };
    });
  }

  get regularMessages(): string[] {
    return this.result.messages.filter(m => !m.includes(' DIAGNOSIS cause:'));
  }

  private readonly ACC_ALARM_TYPES = new Set<AlarmType>([
    'OVERLOADED_DRIVER', 'OVERLOADED_TRUCK', 'PROBLEMATIC_ROUTE',
    'SHIFT_WARNING', 'FLEET_SHORTAGE', 'ORDER_BACKLOG'
  ]);

  get accumulateAlarms(): Alarm[] {
    return this.result.alarms.filter(a => this.ACC_ALARM_TYPES.has(a.type));
  }

  get otherAlarms(): Alarm[] {
    return this.result.alarms.filter(a => !this.ACC_ALARM_TYPES.has(a.type));
  }

  accAlarmIcon(type: string): string {
    const m: Record<string, string> = {
      OVERLOADED_DRIVER: '😴', OVERLOADED_TRUCK: '⚖️', PROBLEMATIC_ROUTE: '🚧',
      SHIFT_WARNING: '🕐', FLEET_SHORTAGE: '🚛', ORDER_BACKLOG: '📦'
    };
    return m[type] ?? '⚠️';
  }

  accAlarmRule(type: string): string {
    const m: Record<string, string> = {
      OVERLOADED_DRIVER:  'collectList — drivers with working hours > 9h',
      OVERLOADED_TRUCK:   'sum — total weight of ASSIGNED orders exceeds truck capacity',
      PROBLEMATIC_ROUTE:  'average — avg delay of IN_PROGRESS orders on route > 30min',
      SHIFT_WARNING:      'average — avg team working hours > 7.5h',
      FLEET_SHORTAGE:     'count — available trucks < 2 while orders are pending',
      ORDER_BACKLOG:      'count — orders in WAITING_RESOURCES > 5'
    };
    return m[type] ?? '';
  }

  messageClass(msg: string): string {
    if (msg.startsWith('[TEMPLATE]'))                                         return 'msg-template';
    if (msg.startsWith('[CRITICAL]'))                                         return 'msg-danger';
    if (msg.startsWith('[ALERT]'))                                            return 'msg-alert';
    if (msg.startsWith('[INFO]'))                                             return 'msg-info';
    if (msg.includes('[WARN]'))                                               return 'msg-warn';
    if (msg.includes('ASSIGNED') || msg.includes('assigned'))                return 'msg-success';
    if (msg.includes('UNFEASIBLE') || msg.includes('excluded')
        || msg.includes('rejected') || msg.includes('ADR'))                  return 'msg-danger';
    if (msg.includes('DOMINO') || msg.includes('ESCALATION')
        || msg.includes('replanned'))                                         return 'msg-domino';
    if (msg.includes('waiting') || msg.includes('WAITING')
        || msg.includes('postponed'))                                         return 'msg-muted';
    return '';
  }
}
