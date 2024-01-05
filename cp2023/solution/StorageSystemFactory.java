/*
 * University of Warsaw
 * Concurrent Programming Course 2023/2024
 * Java Assignment
 *
 * Author: Konrad Iwanicki (iwanicki@mimuw.edu.pl)
 */
package cp2023.solution;

import java.util.*;
import java.util.stream.Collectors;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;


public final class StorageSystemFactory {

    public static StorageSystem newSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
        // Check validity.
        if (deviceTotalSlots == null) throw new IllegalArgumentException("Argument deviceTotalSlots is null.");
        if (componentPlacement == null) throw new IllegalArgumentException("Argument componentPlacement is null.");
        if (deviceTotalSlots.isEmpty()) throw new IllegalArgumentException("Argument deviceTotalSlots is empty.");
        if (componentPlacement.isEmpty()) throw new IllegalArgumentException("Argument componentPlacement is empty.");

        Set<ComponentId> componentIds = new HashSet<>(componentPlacement.keySet());
        Set<DeviceId> deviceIds = new HashSet<>(deviceTotalSlots.keySet());
        Map<DeviceId, List<ComponentId>> deviceContents = new HashMap<>();

        for (Map.Entry<DeviceId, Integer> device : deviceTotalSlots.entrySet()) {
            if (device.getKey() == null) throw new IllegalArgumentException("A null key in devices.");
            if (device.getValue() == null) throw new IllegalArgumentException("A null capacity in devices.");
            if (device.getValue() <= 0) throw new IllegalArgumentException("A non positive capacity in devices.");
            deviceContents.put(device.getKey(), new LinkedList<>());
        }
        for (Map.Entry<ComponentId, DeviceId> component : componentPlacement.entrySet()) {
            if (component.getKey() == null) throw new IllegalArgumentException("A null key in components.");
            if (component.getValue() == null) throw new IllegalArgumentException("A null device in components.");
            if (!deviceIds.contains(component.getValue())) throw new IllegalArgumentException("An unregistered device in components.");
            deviceContents.get(component.getValue()).add(component.getKey());
        }
        for (Map.Entry<DeviceId, List<ComponentId>> content : deviceContents.entrySet()) {
            if (content.getValue().size() > deviceTotalSlots.get(content.getKey())) throw new IllegalArgumentException("A device has more components than its capacity.");
        }
        // Return the system.
        return new cp2023.solution.StorageSystem(deviceTotalSlots, new LinkedList<>(componentIds), deviceContents);
    }

}
