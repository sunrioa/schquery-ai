package cn.ling.service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.List;

public class text {
    public static void main(String[] args) {
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        OperatingSystem os = systemInfo.getOperatingSystem();
        FileSystem fileSystem = os.getFileSystem();
        List<NetworkIF> networkIFs = systemInfo.getHardware().getNetworkIFs();

        // CPU 使用率
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        System.out.println("CPU Load: " + String.format("%.2f", cpuLoad) + "%");

        // 内存使用情况
        long totalMemory = memory.getTotal();
        long usedMemory = totalMemory - memory.getAvailable();
        System.out.println("Used Memory: " + (usedMemory / (1024 * 1024)) + " MB");
        System.out.println("Total Memory: " + (totalMemory / (1024 * 1024)) + " MB");

        // 磁盘使用情况
        for (OSFileStore fileStore : fileSystem.getFileStores()) {
            System.out.println("Disk: " + fileStore.getMount() 
                    + ", Total: " + (fileStore.getTotalSpace() / (1024 * 1024)) + " MB"
                    + ", Usable: " + (fileStore.getUsableSpace() / (1024 * 1024)) + " MB"
                    + ", Used: " + ((fileStore.getTotalSpace() - fileStore.getUsableSpace()) / (1024 * 1024)) + " MB");
        }

        // 网络带宽使用情况
        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            long bytesRecv = net.getBytesRecv();
            long bytesSent = net.getBytesSent();
            System.out.println("Network Interface: " + net.getName());
            System.out.println("Received: " + (bytesRecv / 1024) + " KB, Sent: " + (bytesSent / 1024) + " KB");
        }
    }
}
