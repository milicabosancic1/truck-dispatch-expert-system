# TruckDispatch -- demo skripta za popunjavanje sistema test podacima
# Pokreni backend (mvn spring-boot:run) pa onda: .\scripts\populate.ps1

param(
    [string]$BaseUrl = "http://localhost:8080/api/dispatch"
)

$headers = @{ "Content-Type" = "application/json" }

function Write-Section($title) {
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Result($result) {
    Write-Host ""
    Write-Host "  Poruke ($($result.messages.Count)):" -ForegroundColor Yellow
    foreach ($msg in $result.messages) {
        Write-Host "    >> $msg" -ForegroundColor Gray
    }

    Write-Host ""
    Write-Host "  Nalozi ($($result.processedOrders.Count)):" -ForegroundColor Yellow
    foreach ($o in $result.processedOrders) {
        $truck  = if ($o.assignedTruckId)  { $o.assignedTruckId }  else { "--" }
        $driver = if ($o.assignedDriverId) { $o.assignedDriverId } else { "--" }
        Write-Host ("    {0,-6} {1,-10} {2,-28} kamion={3,-5} vozac={4}" -f `
            $o.id, $o.status, $o.destination, $truck, $driver) -ForegroundColor White
    }

    if ($result.alarms.Count -gt 0) {
        Write-Host ""
        Write-Host "  Alarmi ($($result.alarms.Count)):" -ForegroundColor Red
        foreach ($a in $result.alarms) {
            Write-Host "    [!] $($a.type) | $($a.entityId) | $($a.message)" -ForegroundColor Red
        }
    }
}

# ============================================================
# SCENARIO 1: Jutarnji spic + zimski uslovi
# Ocekivano: MORNING_PEAK + WINTER_CONDITIONS kontekst,
#            RASHLADNI nalog trazi kamion sa rashladnom jedinicom,
#            ADR nalog trazi ADR opremu i licencu
# ============================================================
Write-Section "SCENARIO 1 -- Jutarnji spic, zimski uslovi (07:00, sreda, -3C)"

$s1 = @{
    temperature = -3
    hour        = 7
    dayOfWeek   = 3
    orders = @(
        @{ id="K-01"; destination="Beograd"; weightKg=1800; cargoType="REFRIGERATED";
           deliveryDeadlineMin=90;  priority="URGENT";   status="NEW"; routeId="R-01" },
        @{ id="K-02"; destination="Subotica"; weightKg=3000; cargoType="STANDARD";
           deliveryDeadlineMin=180; priority="HIGH";     status="NEW"; routeId="R-02" },
        @{ id="K-03"; destination="Nis";     weightKg=500;  cargoType="HAZARDOUS";
           deliveryDeadlineMin=240; priority="NORMAL";   status="NEW"; routeId="R-03" }
    )
    trucks = @(
        @{ id="V-01"; type="MEDIUM"; maxCapacityKg=5000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=80; hasRefrigerationUnit=$true;
           hasAdrEquipment=$false; distanceToOriginKm=5; daysSinceRefrigerationService=10 },
        @{ id="V-02"; type="LARGE"; maxCapacityKg=12000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=90; hasRefrigerationUnit=$false;
           hasAdrEquipment=$true; distanceToOriginKm=8; daysSinceRefrigerationService=0 },
        @{ id="V-03"; type="SMALL"; maxCapacityKg=2000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=60; hasRefrigerationUnit=$false;
           hasAdrEquipment=$false; distanceToOriginKm=3; daysSinceRefrigerationService=0 }
    )
    drivers = @(
        @{ id="D-01"; available=$true; workingHoursToday=3; license="CE";
           hasAdrLicense=$false; fatigueLevel=1; yearsOfExperience=8; recentRouteIds=@() },
        @{ id="D-02"; available=$true; workingHoursToday=2; license="CE";
           hasAdrLicense=$true;  fatigueLevel=0; yearsOfExperience=12; recentRouteIds=@() },
        @{ id="D-03"; available=$true; workingHoursToday=5; license="CE";
           hasAdrLicense=$false; fatigueLevel=3; yearsOfExperience=2; recentRouteIds=@() }
    )
    routes = @(
        @{ id="R-01"; roadType="REGIONAL"; distanceKm=80;  estimatedTimeHours=1.5;
           maxCapacityKg=10000; maxSpeedKmh=90; hasTunnel=$false },
        @{ id="R-02"; roadType="HIGHWAY";  distanceKm=150; estimatedTimeHours=2.0;
           maxCapacityKg=20000; maxSpeedKmh=130; hasTunnel=$false },
        @{ id="R-03"; roadType="REGIONAL"; distanceKm=200; estimatedTimeHours=3.0;
           maxCapacityKg=15000; maxSpeedKmh=90; hasTunnel=$true }
    )
} | ConvertTo-Json -Depth 5

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/process" -Method Post -Headers $headers -Body $s1
    Write-Result $r
} catch {
    Write-Host "  GRESKA: $($_.Exception.Message)" -ForegroundColor Red
}

# ============================================================
# SCENARIO 2: Vikend, opasna roba + lomljivi teret
# Ocekivano: WEEKEND kontekst (60% flote), ADR provera,
#            samo URGENT i HIGH nalozi prolaze
# ============================================================
Write-Section "SCENARIO 2 -- Vikend, opasna roba (14:00, subota, 12C)"

$s2 = @{
    temperature = 12
    hour        = 14
    dayOfWeek   = 6
    orders = @(
        @{ id="K-04"; destination="Zagreb";     weightKg=800;  cargoType="HAZARDOUS";
           deliveryDeadlineMin=300; priority="URGENT";  status="NEW"; routeId="R-04" },
        @{ id="K-05"; destination="Beograd";    weightKg=200;  cargoType="FRAGILE";
           deliveryDeadlineMin=120; priority="HIGH";    status="NEW"; routeId="R-05" },
        @{ id="K-06"; destination="Kragujevac"; weightKg=4000; cargoType="STANDARD";
           deliveryDeadlineMin=360; priority="NORMAL";  status="NEW"; routeId="R-05" }
    )
    trucks = @(
        @{ id="V-04"; type="MEDIUM"; maxCapacityKg=5000; status="AVAILABLE";
           location="Beograd"; fuelPercent=95; hasRefrigerationUnit=$false;
           hasAdrEquipment=$true; distanceToOriginKm=12; daysSinceRefrigerationService=0 },
        @{ id="V-05"; type="SMALL"; maxCapacityKg=1500; status="AVAILABLE";
           location="Beograd"; fuelPercent=70; hasRefrigerationUnit=$false;
           hasAdrEquipment=$false; distanceToOriginKm=6; daysSinceRefrigerationService=0 }
    )
    drivers = @(
        @{ id="D-04"; available=$true; workingHoursToday=1; license="CE";
           hasAdrLicense=$true;  fatigueLevel=0; yearsOfExperience=15; recentRouteIds=@() },
        @{ id="D-05"; available=$true; workingHoursToday=4; license="CE";
           hasAdrLicense=$false; fatigueLevel=2; yearsOfExperience=3; recentRouteIds=@() }
    )
    routes = @(
        @{ id="R-04"; roadType="HIGHWAY";  distanceKm=350; estimatedTimeHours=4.0;
           maxCapacityKg=15000; maxSpeedKmh=130; hasTunnel=$false },
        @{ id="R-05"; roadType="REGIONAL"; distanceKm=120; estimatedTimeHours=2.0;
           maxCapacityKg=8000;  maxSpeedKmh=80;  hasTunnel=$false }
    )
} | ConvertTo-Json -Depth 5

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/process" -Method Post -Headers $headers -Body $s2
    Write-Result $r
} catch {
    Write-Host "  GRESKA: $($_.Exception.Message)" -ForegroundColor Red
}

# ============================================================
# SCENARIO 3: Nocni rezim, samo URGENT prolazi
# Ocekivano: NIGHT_MODE kontekst, NORMAL i HIGH nalozi
#            postaju POSTPONED_UNTIL_MORNING
# ============================================================
Write-Section "SCENARIO 3 -- Nocni rezim (22:00, cetvrtak, 8C)"

$s3 = @{
    temperature = 8
    hour        = 22
    dayOfWeek   = 4
    orders = @(
        @{ id="K-07"; destination="Beograd";  weightKg=500;  cargoType="STANDARD";
           deliveryDeadlineMin=60;  priority="URGENT";  status="NEW"; routeId="R-06" },
        @{ id="K-08"; destination="Subotica"; weightKg=2000; cargoType="STANDARD";
           deliveryDeadlineMin=180; priority="HIGH";    status="NEW"; routeId="R-06" },
        @{ id="K-09"; destination="Pancevo";  weightKg=300;  cargoType="REFRIGERATED";
           deliveryDeadlineMin=120; priority="NORMAL";  status="NEW"; routeId="R-06" }
    )
    trucks = @(
        @{ id="V-06"; type="SMALL"; maxCapacityKg=2000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=85; hasRefrigerationUnit=$true;
           hasAdrEquipment=$false; distanceToOriginKm=4; daysSinceRefrigerationService=3 }
    )
    drivers = @(
        @{ id="D-06"; available=$true; workingHoursToday=6; license="CE";
           hasAdrLicense=$false; fatigueLevel=4; yearsOfExperience=6; recentRouteIds=@() }
    )
    routes = @(
        @{ id="R-06"; roadType="CITY"; distanceKm=30; estimatedTimeHours=0.75;
           maxCapacityKg=5000; maxSpeedKmh=50; hasTunnel=$false }
    )
} | ConvertTo-Json -Depth 5

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/process" -Method Post -Headers $headers -Body $s3
    Write-Result $r
} catch {
    Write-Host "  GRESKA: $($_.Exception.Message)" -ForegroundColor Red
}

# ============================================================
# SCENARIO 4: minTruckType + nocni rezim -- fatigue constraint
# Ocekivano: SMALL kamion iskljucen (nalog zahteva min MEDIUM),
#            vozac sa fatigueLevel=6 iskljucen u nocnom rezimu
# ============================================================
Write-Section "SCENARIO 4 -- minTruckType filtriranje + nocni fatigue (22:00, 8C)"

$s4 = @{
    temperature = 8
    hour        = 22
    dayOfWeek   = 3
    orders = @(
        @{ id="K-10"; destination="Beograd"; weightKg=500; cargoType="STANDARD";
           deliveryDeadlineMin=50; priority="URGENT"; status="NEW"; routeId="R-07";
           minTruckType="MEDIUM" }
    )
    trucks = @(
        @{ id="V-07"; type="SMALL";  maxCapacityKg=2000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=90; hasRefrigerationUnit=$false;
           hasAdrEquipment=$false; distanceToOriginKm=3; daysSinceRefrigerationService=0 },
        @{ id="V-08"; type="MEDIUM"; maxCapacityKg=5000; status="AVAILABLE";
           location="Novi Sad"; fuelPercent=90; hasRefrigerationUnit=$false;
           hasAdrEquipment=$false; distanceToOriginKm=8; daysSinceRefrigerationService=0 }
    )
    drivers = @(
        @{ id="D-07"; available=$true; workingHoursToday=2; license="CE";
           hasAdrLicense=$false; fatigueLevel=6; yearsOfExperience=5; recentRouteIds=@() },
        @{ id="D-08"; available=$true; workingHoursToday=2; license="CE";
           hasAdrLicense=$false; fatigueLevel=3; yearsOfExperience=5; recentRouteIds=@() }
    )
    routes = @(
        @{ id="R-07"; roadType="HIGHWAY"; distanceKm=100; estimatedTimeHours=1.0;
           maxCapacityKg=15000; maxSpeedKmh=130; hasTunnel=$false }
    )
} | ConvertTo-Json -Depth 5

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/process" -Method Post -Headers $headers -Body $s4
    Write-Result $r
} catch {
    Write-Host "  GRESKA: $($_.Exception.Message)" -ForegroundColor Red
}

# ============================================================
# SCENARIO 5: UNFEASIBLE + Vecernji spic
# Ocekivano: K-11 pretezak (25000kg > 12000kg kapacitet) -> UNFEASIBLE,
#            K-12 dobija kamion, vecernji spic aktivan (17-20h)
# ============================================================
Write-Section "SCENARIO 5 -- UNFEASIBLE nalog + vecernji spic (18:00, utorak, 10C)"

$s5 = @{
    temperature = 10
    hour        = 18
    dayOfWeek   = 2
    orders = @(
        @{ id="K-11"; destination="Beograd"; weightKg=25000; cargoType="STANDARD";
           deliveryDeadlineMin=300; priority="NORMAL"; status="NEW"; routeId="R-08" },
        @{ id="K-12"; destination="Novi Sad"; weightKg=1000; cargoType="STANDARD";
           deliveryDeadlineMin=200; priority="HIGH"; status="NEW"; routeId="R-08" }
    )
    trucks = @(
        @{ id="V-09"; type="LARGE"; maxCapacityKg=12000; status="AVAILABLE";
           location="Beograd"; fuelPercent=85; hasRefrigerationUnit=$false;
           hasAdrEquipment=$false; distanceToOriginKm=5; daysSinceRefrigerationService=0 }
    )
    drivers = @(
        @{ id="D-09"; available=$true; workingHoursToday=2; license="CE";
           hasAdrLicense=$false; fatigueLevel=1; yearsOfExperience=7; recentRouteIds=@() }
    )
    routes = @(
        @{ id="R-08"; roadType="HIGHWAY"; distanceKm=80; estimatedTimeHours=1.0;
           maxCapacityKg=30000; maxSpeedKmh=130; hasTunnel=$false }
    )
} | ConvertTo-Json -Depth 5

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/process" -Method Post -Headers $headers -Body $s5
    Write-Result $r
} catch {
    Write-Host "  GRESKA: $($_.Exception.Message)" -ForegroundColor Red
}
