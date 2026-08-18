package com.cloudbox.agent.metrics;

import java.util.List;

import org.springframework.stereotype.Component;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

@Component
public class MetricsCollector {

    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final Sensors sensors;
    private final OperatingSystem operatingSystem;
    private long[] previousCpuTicks;

    public MetricsCollector() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        this.processor = hardware.getProcessor();
        this.memory = hardware.getMemory();
        this.sensors = hardware.getSensors();
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.previousCpuTicks = processor.getSystemCpuLoadTicks();
    }

    public synchronized SystemMetrics collect() {
        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(previousCpuTicks);
        previousCpuTicks = processor.getSystemCpuLoadTicks();

        List<OSFileStore> fileStores = operatingSystem.getFileSystem().getFileStores(true);
        long diskTotal = fileStores.stream()
                .mapToLong(OSFileStore::getTotalSpace)
                .filter(space -> space > 0)
                .sum();
        long diskFree = fileStores.stream()
                .filter(store -> store.getTotalSpace() > 0)
                .mapToLong(OSFileStore::getUsableSpace)
                .sum();

        return new SystemMetrics(
                clampPercent((1.0 - cpuUsage) * 100.0),
                memory.getAvailable(),
                memory.getTotal(),
                diskFree,
                diskTotal,
                availableTemperature(sensors.getCpuTemperature()));
    }

    static double clampPercent(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    static Double availableTemperature(double temperature) {
        return Double.isFinite(temperature) && temperature > 0.0 ? temperature : null;
    }
}
