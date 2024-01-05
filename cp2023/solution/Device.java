package cp2023.solution;

import cp2023.base.DeviceId;

import java.util.*;
import java.util.concurrent.Semaphore;


class Device {
    private final DeviceId id;
    private final int capacity;
    private Integer reserved;
    // Components waiting to reserve on this device.
    private final List<Component> waiting;
    private final List<Component> present;
    private final List<Component> leaving;

    public Device(DeviceId id, int capacity, List<Component> list) {
        this.id = id;
        this.capacity = capacity;
        reserved = 0;
        waiting = new ArrayList<>();
        present = new ArrayList<>();
        leaving = new ArrayList<>();

        for (Component component : list) {
            ++reserved;
            component.setDevice(this);
            present.add(component);
        }
    }

    // ---------- GETTERS AND SETTERS ----------
    public DeviceId getId() {
        return id;
    }
    public int getUnreserved() {
        return Math.max(0, capacity - reserved);
    }
    // Return whether there is any slot in this device that we can reserve without asking or waiting for anything.
    public boolean hasEmptySlots() {
        return present.size() < capacity;
    }
    public List<Component> getWaiting() {
        return waiting;
    }
    // -----------------------------------------

    public void reserve(Component component) {
        chooseLeaving(component);
        present.add(component);
        ++reserved;
    }
    public void reserveWithReplacement(Component component, Component replacement) {
        replacement.selectForReplacement(component);
        present.add(component);
        ++reserved;
    }
    public void moveToLeaving(Component component) {
        leaving.add(component);
        --reserved;
    }
    public void remove(Component component) {
        present.remove(component);
        leaving.remove(component);
    }

    // If there are no unreserved spots,
    // choose some component that is leaving
    // and mark it as being waited on,
    // otherwise return null.
    //
    // It is guaranteed that if choosing
    // occurs, there will be at least one leaving
    // component that has not yet been assigned
    // to another entering component.
    public void chooseLeaving(Component choosingComponent) {
        if (hasEmptySlots()) {
            choosingComponent.setNullReplacement();
            return;
        }

        for (Component component : leaving) {
            if (!component.isSelectedForReplacement())  {
                component.selectForReplacement(choosingComponent);
                return;
            }
        }

        throw new RuntimeException("panic: no leaving component found despite guarantee!");
    }

    // Let the first component waiting for this device reserve its spot
    // and give them the critical section.
    // If none exist, release mutex.
    public void letReserve(Semaphore mutexToRelease) {
        if (!waiting.isEmpty()) {
            waiting.get(0).getReservation().release();
        } else {
            mutexToRelease.release();
        }
    }
}
