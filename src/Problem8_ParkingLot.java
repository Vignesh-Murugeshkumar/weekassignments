import java.util.*;

/**
 * Problem 8: Parking Lot Management with Open Addressing
 *
 * Concepts: Array-based hash table, open addressing with linear probing,
 *           custom hash function, EMPTY/OCCUPIED/DELETED states, load factor.
 */
public class Problem8_ParkingLot {

    enum SpotStatus { EMPTY, OCCUPIED, DELETED }

    /**
     * Represents a parked vehicle in a spot.
     */
    static class ParkingRecord {
        final String licensePlate;
        final int spotNumber;
        final long entryTime;
        long exitTime;
        int probesRequired;

        ParkingRecord(String licensePlate, int spotNumber, int probesRequired) {
            this.licensePlate = licensePlate;
            this.spotNumber = spotNumber;
            this.entryTime = System.currentTimeMillis();
            this.probesRequired = probesRequired;
        }

        long getDurationMinutes() {
            long end = exitTime > 0 ? exitTime : System.currentTimeMillis();
            return (end - entryTime) / (60 * 1000);
        }

        double getFee(double ratePerHour) {
            double hours = getDurationMinutes() / 60.0;
            return Math.max(hours, 0.5) * ratePerHour; // Minimum 30-minute charge
        }
    }

    static class ParkResult {
        final boolean success;
        final int spotNumber;
        final int probes;
        final String message;

        ParkResult(boolean success, int spotNumber, int probes, String message) {
            this.success = success;
            this.spotNumber = spotNumber;
            this.probes = probes;
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    static class ExitResult {
        final String licensePlate;
        final int spotNumber;
        final long durationMinutes;
        final double fee;

        ExitResult(String licensePlate, int spotNumber, long durationMinutes, double fee) {
            this.licensePlate = licensePlate;
            this.spotNumber = spotNumber;
            this.durationMinutes = durationMinutes;
            this.fee = fee;
        }

        @Override
        public String toString() {
            long hours = durationMinutes / 60;
            long mins = durationMinutes % 60;
            return String.format("Spot #%d freed, Duration: %dh %dm, Fee: $%.2f",
                    spotNumber, hours, mins, fee);
        }
    }

    // Core data structure: array-based hash table with open addressing
    private final SpotStatus[] spotStatus;
    private final ParkingRecord[] spots;
    private final int capacity;
    private int occupiedCount = 0;
    private final double ratePerHour;

    // License plate -> spot number (reverse lookup)
    private final Map<String, Integer> vehicleSpotMap = new HashMap<>();

    // Statistics
    private int totalProbes = 0;
    private int totalParkOperations = 0;
    private final Map<Integer, Integer> hourlyOccupancy = new TreeMap<>(); // hour -> count

    public Problem8_ParkingLot(int capacity, double ratePerHour) {
        this.capacity = capacity;
        this.ratePerHour = ratePerHour;
        this.spots = new ParkingRecord[capacity];
        this.spotStatus = new SpotStatus[capacity];
        Arrays.fill(spotStatus, SpotStatus.EMPTY);
    }

    /**
     * Custom hash function: maps license plate to a preferred spot number.
     */
    private int hash(String licensePlate) {
        // Use a good hash function to distribute evenly
        int hash = 0;
        for (int i = 0; i < licensePlate.length(); i++) {
            hash = 31 * hash + licensePlate.charAt(i);
        }
        return Math.floorMod(hash, capacity); // Always non-negative
    }

    /**
     * Park a vehicle using linear probing. O(1) average, O(n) worst case.
     */
    public ParkResult parkVehicle(String licensePlate) {
        if (occupiedCount >= capacity) {
            return new ParkResult(false, -1, 0, "Parking lot is full!");
        }

        if (vehicleSpotMap.containsKey(licensePlate)) {
            return new ParkResult(false, -1, 0,
                    "Vehicle " + licensePlate + " is already parked at spot #" + vehicleSpotMap.get(licensePlate));
        }

        int preferredSpot = hash(licensePlate);
        int probes = 0;

        // Linear probing
        int spot = preferredSpot;
        while (spotStatus[spot] == SpotStatus.OCCUPIED) {
            probes++;
            spot = (spot + 1) % capacity; // Wrap around
            if (spot == preferredSpot) {
                // Full circle — shouldn't happen since we check occupiedCount
                return new ParkResult(false, -1, probes, "No available spots (full probe cycle)");
            }
        }

        // Found an empty or deleted spot
        ParkingRecord record = new ParkingRecord(licensePlate, spot, probes);
        spots[spot] = record;
        spotStatus[spot] = SpotStatus.OCCUPIED;
        vehicleSpotMap.put(licensePlate, spot);
        occupiedCount++;

        // Track statistics
        totalProbes += probes;
        totalParkOperations++;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        hourlyOccupancy.merge(hour, 1, Integer::sum);

        String probeMsg = probes == 0
                ? String.format("Assigned spot #%d (0 probes)", spot)
                : String.format("Assigned spot #%d (%d probe%s, preferred was #%d)",
                spot, probes, probes == 1 ? "" : "s", preferredSpot);

        return new ParkResult(true, spot, probes, probeMsg);
    }

    /**
     * Exit a vehicle. Uses DELETED tombstone for proper open addressing.
     */
    public ExitResult exitVehicle(String licensePlate) {
        Integer spotNum = vehicleSpotMap.get(licensePlate);
        if (spotNum == null) {
            throw new IllegalArgumentException("Vehicle not found: " + licensePlate);
        }

        ParkingRecord record = spots[spotNum];
        record.exitTime = System.currentTimeMillis();
        double fee = record.getFee(ratePerHour);

        // Mark as DELETED (not EMPTY) to maintain probe chains
        spotStatus[spotNum] = SpotStatus.DELETED;
        spots[spotNum] = null;
        vehicleSpotMap.remove(licensePlate);
        occupiedCount--;

        return new ExitResult(licensePlate, spotNum, record.getDurationMinutes(), fee);
    }

    /**
     * Find a vehicle's spot number. O(1) via reverse lookup map.
     */
    public int findVehicle(String licensePlate) {
        return vehicleSpotMap.getOrDefault(licensePlate, -1);
    }

    /**
     * Find nearest available spot to entrance (spot #0).
     */
    public int findNearestAvailableSpot() {
        for (int i = 0; i < capacity; i++) {
            if (spotStatus[i] != SpotStatus.OCCUPIED) {
                return i;
            }
        }
        return -1; // Full
    }

    /**
     * Get current load factor.
     */
    public double getLoadFactor() {
        return (double) occupiedCount / capacity;
    }

    /**
     * Get parking statistics.
     */
    public String getStatistics() {
        double avgProbes = totalParkOperations > 0 ? (double) totalProbes / totalParkOperations : 0;
        int peakHour = -1;
        int peakCount = 0;
        for (Map.Entry<Integer, Integer> entry : hourlyOccupancy.entrySet()) {
            if (entry.getValue() > peakCount) {
                peakCount = entry.getValue();
                peakHour = entry.getKey();
            }
        }
        String peak = peakHour >= 0
                ? String.format("%d:00-%d:00", peakHour, peakHour + 1)
                : "N/A";

        return String.format(
                "Occupancy: %.1f%% (%d/%d), Avg Probes: %.1f, Total Park Ops: %d, Peak Hour: %s",
                getLoadFactor() * 100, occupiedCount, capacity, avgProbes, totalParkOperations, peak
        );
    }

    // ======================== DEMO ========================
    public static void main(String[] args) throws InterruptedException {
        Problem8_ParkingLot lot = new Problem8_ParkingLot(500, 5.50); // 500 spots, $5.50/hr

        System.out.println("=== Smart Parking Lot (Open Addressing) ===");
        System.out.println("Capacity: 500 spots, Rate: $5.50/hr\n");

        // Park vehicles demonstrating collision handling
        System.out.println("=== Parking Vehicles ===");
        String[] plates = {"ABC-1234", "ABC-1235", "XYZ-9999", "DEF-5678", "GHI-0001"};
        for (String plate : plates) {
            ParkResult result = lot.parkVehicle(plate);
            System.out.printf("parkVehicle(\"%s\") -> %s%n", plate, result);
        }

        // Find a vehicle
        System.out.println("\n=== Finding Vehicle ===");
        System.out.printf("findVehicle(\"ABC-1234\") -> Spot #%d%n", lot.findVehicle("ABC-1234"));
        System.out.printf("findVehicle(\"UNKNOWN\")  -> Spot #%d (not found)%n", lot.findVehicle("UNKNOWN"));

        // Nearest available spot
        System.out.printf("Nearest available spot to entrance: #%d%n", lot.findNearestAvailableSpot());

        // Simulate time passing and exit
        System.out.println("\n=== Vehicle Exit ===");
        Thread.sleep(100); // Simulate some parking time
        ExitResult exit = lot.exitVehicle("ABC-1234");
        System.out.printf("exitVehicle(\"ABC-1234\") -> %s%n", exit);

        // Park many vehicles to show load factor effects
        System.out.println("\n=== Filling Parking Lot ===");
        Random random = new Random(42);
        int probeSum = 0;
        int maxProbes = 0;
        for (int i = 0; i < 390; i++) { // Fill to ~78%
            String plate = String.format("AUTO-%04d", i);
            ParkResult result = lot.parkVehicle(plate);
            probeSum += result.probes;
            maxProbes = Math.max(maxProbes, result.probes);
        }
        System.out.printf("Parked 390 additional vehicles%n");
        System.out.printf("Max probes in batch: %d%n", maxProbes);

        System.out.println("\n=== Statistics ===");
        System.out.println(lot.getStatistics());
    }
}
