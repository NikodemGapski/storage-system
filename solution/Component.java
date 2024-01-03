package cp2023.solution;

import cp2023.base.ComponentId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

class Component {
    private final ComponentId id;
    private final Semaphore reservationSemaphore;
    private final Semaphore otherWaitingSemaphore;
    private Component sourceForReplacement;
    private Component destinationReplacement;
    private boolean isWaitingForReplacement;
    private boolean isOperatedOn;
    private Device currentDevice;
    private Device destinationDevice;
    private List<Component> path;

    public Component(ComponentId id) {
        this.id = id;
        reservationSemaphore = new Semaphore(0, true);
        otherWaitingSemaphore = new Semaphore(0, true);
        isOperatedOn = false;
    }

    // ---------- GETTERS AND SETTERS ----------
    public ComponentId getId() {
        return id;
    }
    public Device getDevice() {
        return currentDevice;
    }
    public void setDevice(Device device) {
        currentDevice = device;
    }
    public void setDestination(Device destination) {
        this.destinationDevice = destination;
    }
    public boolean isSelectedForReplacement() {
        return sourceForReplacement != null;
    }
    public void setNullReplacement() {
        destinationReplacement = null;
    }
    public void selectForReplacement(Component selectingComponent) {
        sourceForReplacement = selectingComponent;
        sourceForReplacement.destinationReplacement = this;
    }
    private void unselectForReplacement() {
        sourceForReplacement.destinationReplacement = null;
        sourceForReplacement = null;
    }
    private boolean isOtherWaiting() {
        return sourceForReplacement != null && sourceForReplacement.isWaitingForReplacement;
    }
    public boolean isOperatedOn() {
        return isOperatedOn;
    }
    public void startOperating() {
        isOperatedOn = true;
    }
    public void endOperating() {
        isOperatedOn = false;
    }
    public List<Component> getPath() {
        return path;
    }
    private void setPath(List<Component> path) {
        this.path = path;
    }
    public Semaphore getReservation() {
        return reservationSemaphore;
    }
    // -----------------------------------------

    public void waitOnReservation(Semaphore mutexToKeep) throws InterruptedException {
        destinationDevice.getWaiting().add(this);
        mutexToKeep.release();
        reservationSemaphore.acquire();
        // Critical section inheritance.
        destinationDevice.getWaiting().remove(this);
    }
    public void wakeUpNextInCycle(Semaphore mutexToRelease) {
        Component next = popPathAndGetBack();
        setPath(null);
        if (next != null) {
            next.getReservation().release();
            // Gave up the critical section.
        } else {
            mutexToRelease.release();
        }
    }
    private void moveToLeaving() {
        if (currentDevice != null) {
            currentDevice.moveToLeaving(this);
        }
    }
    private void reserve() {
        if (destinationDevice != null) {
            destinationDevice.reserve(this);
        }
    }
    private void reserveWithReplacement(Component replacement) {
        destinationDevice.reserveWithReplacement(this, replacement);
    }
    public void beginReservation() {
        moveToLeaving();
        reserve();
    }
    public static void beginCycleReservation(List<Component> cycle) {
        for (Component component : cycle) {
            component.moveToLeaving();
        }
        for (int i = 0; i < cycle.size(); ++i) {
            Component current = cycle.get(i);
            Component next = cycle.get((i + 1) % cycle.size());
            current.reserveWithReplacement(next);
        }
    }

    // Find a cycle containing this component.
    // Cycle choice:
    // - the algorithm searches the waiting tree
    //   from devices to components that are waiting
    //   on these devices, preferring the ones
    //   that came earlier.
    public boolean findCycle(Component current) {
        return findCycle(current, new HashSet<>());
    }
    private boolean findCycle(Component current, Set<Device> visited) {
        if (current.getDevice() == destinationDevice) {
            // We've encountered a cycle.
            current.setPath(new ArrayList<>());
            current.getPath().add(current);
            return true;
        }

        Device device = current.getDevice();
        visited.add(device);

        for (Component next : device.getWaiting()) {
            if (visited.contains(next.getDevice())) continue;
            if (next.getDevice() == null) continue;

            findCycle(next, visited);

            if (next.getPath() != null) {
                // A cycle has been found.
                current.setPath(next.getPath());
                current.getPath().add(current);
                return true;
            }
        }
        return false;
    }

    public Component popPathAndGetBack() {
        path.remove(path.size() - 1);
        return path.size() - 1 >= 0 ? path.get(path.size() - 1) : null;
    }
    public void waitForReplacement(Semaphore mutexToKeep) throws InterruptedException {
        if (destinationReplacement != null) {
            isWaitingForReplacement = true;
            destinationReplacement.waitOn(mutexToKeep);
            isWaitingForReplacement = false;
            // Critical section inheritance.
        }
    }

    // The caller component must wait until
    // this component's otherWaiting semaphore
    // is released.
    public void waitOn(Semaphore mutexToKeep) throws InterruptedException {
        mutexToKeep.release();
        otherWaitingSemaphore.acquire();
        // Critical section inheritance.
    }
    public void removeFromCurrent() {
        if (currentDevice != null) {
            currentDevice.remove(this);
        }
    }
    public void notifyReplacement(Semaphore mutexToRelease) {
        if (isOtherWaiting()) {
            otherWaitingSemaphore.release();
            // Gave up the critical section.
        } else {
            if (isSelectedForReplacement()) {
                // Tell the selecting component not to wait for us.
                unselectForReplacement();
            }
            mutexToRelease.release();
        }
    }
    public void arriveAtDestination() {
        // on destinationDevice we're already present
        // since beginReservation().
        currentDevice = destinationDevice;
        destinationDevice = null;
    }
}
