package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.ComponentTransfer;
import cp2023.base.DeviceId;
import cp2023.exceptions.*;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

class StorageSystem implements cp2023.base.StorageSystem {
    private final Map<DeviceId, Device> devices;
    private final Map<ComponentId, Component> components;
    private final Semaphore mutex;

    private enum TransferType {
        ADD,
        MOVE,
        REMOVE
    }
    public StorageSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            List<ComponentId> componentIdList,
            Map<DeviceId, List<ComponentId>> deviceContents) {
        mutex = new Semaphore(1, true);
        components = componentIdList.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        Component::new,
                        (prev, next) -> next,
                        HashMap::new
                ));
        devices = deviceTotalSlots.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new Device(
                                entry.getKey(),
                                entry.getValue(),
                                deviceContents.get(entry.getKey()).stream()
                                        .map(components::get)
                                        .collect(Collectors.toCollection(ArrayList::new))
                        ),
                        (prev, next) -> next,
                        HashMap::new
                ));
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        try {
            mutex.acquire();
            TransferType type;
            try {
                type = checkTransfer(transfer);
            } catch (TransferException e) {
                mutex.release();
                throw e;
            }

            switch (type) {
                case ADD -> addComponent(transfer);
                case MOVE -> moveComponent(transfer);
                case REMOVE -> removeComponent(transfer);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    // Check transfer validity and return its type.
    private TransferType checkTransfer(ComponentTransfer transfer) throws TransferException {
        ComponentId componentId = transfer.getComponentId();
        DeviceId sourceId = transfer.getSourceDeviceId();
        DeviceId destinationId = transfer.getDestinationDeviceId();

        // Check transfer validity (it is guaranteed that componentId != null).
        Component component = components.get(componentId);

        if (sourceId == null && destinationId == null)
            throw new IllegalTransferType(componentId);

        if (sourceId != null && !devices.containsKey(sourceId))
            throw new DeviceDoesNotExist(sourceId);
        if (destinationId != null && !devices.containsKey(destinationId))
            throw new DeviceDoesNotExist(destinationId);

        if (sourceId == null && component != null)
            throw new ComponentAlreadyExists(componentId, component.getDevice().getId());

        if (sourceId != null && component == null)
            throw new ComponentDoesNotExist(componentId, sourceId);
        if (sourceId != null && !component.getDevice().getId().equals(sourceId))
            throw new ComponentDoesNotExist(componentId, sourceId);

        if (destinationId != null && component != null && destinationId.equals(component.getDevice().getId()))
            throw new ComponentDoesNotNeedTransfer(componentId, destinationId);

        if (component != null && component.isOperatedOn())
            throw new ComponentIsBeingOperatedOn(componentId);

        // Return transfer type.
        if (sourceId == null) return TransferType.ADD;
        if (destinationId == null) return TransferType.REMOVE;
        return TransferType.MOVE;
    }
    private void addComponent(ComponentTransfer transfer) throws InterruptedException {
        Component component = new Component(transfer.getComponentId());
        components.put(component.getId(), component);
        Device destination = devices.get(transfer.getDestinationDeviceId());

        setupPreparingNew(component, destination);
        transfer.prepare();
        // No need to finalize preparing as no one is waiting for us.
        setupPerforming(component);
        transfer.perform();
        finalizePerforming(component);
    }
    private void moveComponent(ComponentTransfer transfer) throws InterruptedException {
        Component component = components.get(transfer.getComponentId());
        Device source = devices.get(transfer.getSourceDeviceId());
        Device destination = devices.get(transfer.getDestinationDeviceId());

        setupPreparingMove(component, source, destination);
        transfer.prepare();
        finalizePreparing(component);
        setupPerforming(component);
        transfer.perform();
        finalizePerforming(component);
    }
    private void removeComponent(ComponentTransfer transfer) throws InterruptedException {
        Component component = components.get(transfer.getComponentId());
        components.remove(component.getId());
        Device source = devices.get(transfer.getSourceDeviceId());

        setupPreparingRemoval(component, source);
        transfer.prepare();
        finalizePreparing(component);
        // No need to set up performing, as we're not waiting for anything.
        transfer.perform();
        finalizePerforming(component);
    }
    private void setupPreparingNew(Component component, Device destination) throws InterruptedException {
        component.startOperating();
        component.setDestination(destination);

        if (destination.getUnreserved() == 0) {
            component.waitOnReservation(mutex);
            // Critical section inheritance.
        }
        component.beginReservation();
        mutex.release();
    }
    private void setupPreparingMove(Component component, Device source, Device destination) throws InterruptedException {
        component.startOperating();
        component.setDestination(destination);

        if (!destination.hasEmptySlots()) {
            boolean foundCycle = component.findCycle(component);

            if (!foundCycle) {
                if (destination.getUnreserved() == 0) {
                    // There is no component for replacement to choose from.
                    component.waitOnReservation(mutex);
                }
                // Critical section inheritance.
            } else {
                // The component has completed the cycle.
                Component.beginCycleReservation(component.getPath());
            }

            if (component.getPath() != null) {
                // The component has either completed the cycle
                // or been awoken as a part of one.
                component.wakeUpNextInCycle(mutex);
                // Gave up the critical section or released mutex.
            } else {
                // The component has been awoken not as a part of a cycle.
                component.beginReservation();
                source.letReserve(mutex);
                // Gave up the critical section or released mutex.
            }
        } else {
            component.beginReservation();
            source.letReserve(mutex);
            // Gave up the critical section or released mutex.
        }
    }
    private void setupPreparingRemoval(Component component, Device source) {
        component.startOperating();
        component.setDestination(null);
        component.beginReservation();
        source.letReserve(mutex);
        // Gave up the critical section or released mutex.
    }
    private void finalizePreparing(Component component) throws InterruptedException {
        mutex.acquire();
        component.removeFromCurrent();
        component.notifyReplacement(mutex);
        // Gave up the critical section or released mutex.
    }
    private void setupPerforming(Component component) throws InterruptedException {
        mutex.acquire();
        component.waitForReplacement(mutex);
        mutex.release();
    }
    private void finalizePerforming(Component component) throws InterruptedException {
        mutex.acquire();
        component.arriveAtDestination();
        component.endOperating();
        mutex.release();
    }
}
