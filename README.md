# Concurrent storage system simulation
A concurrent programming course project regarding concurrency in Java.

## Intro
Storage systems often transfer fragments of data across devices to ensure performance in accessing the data, effective use of storage, and resilience to said devices' potential failures. Your job will be to implement the underlying mechanisms coordinating such transfers concurrently in Java, in accordance with the specifications stated below.

## Specifications
In our model data is grouped in components and stored in devices as such. Each device and component have their own unique id (an object of the class `cp2023.base.DeviceId` and `cp2023.base.ComponentId` accordingly). Moreover, every device has its own capacity, that is, the highest number of components it can store at any given moment. The system (an object implementing the `cp2023.base.StorageSystem` interface) is responsible for managing the assignment of components to devices:

```java
public interface StorageSystem {
	void execute(ComponentTransfer transfer) throws TransferException;
}
```

Every component existing within the system is stored on exactly one device, unless the user has ordered its transfer to another one (by calling the `execute` method of the `StorageSystem` class and passing it an argument implementing the `cp2023.base.ComponentTransfer` interface representing the ordered transfer).

```java
public interface ComponentTransfer {
	public ComponentId getComponentId();
	public DeviceId getSourceDeviceId();
	public DeviceId getDestinationDeviceId();
	public void prepare();
	public void perform();
}
```

A component transfer is ordered also when the user decides to add a new component to the system (in that scenario the `getSourceDeviceId` method of the transfer object returns `null`) or remove an existing component from it (then, symmetrically, `getDestinationDeviceId` returns `null`). In other words, a single transfer represents one of three available operations on a component:
- *addition* of a new component onto a system device (`getSourceDeviceId` returns `null` and `getDestinationDeviceId` a not-`null` device id where the component is to be stored),
- *moving* of an existing component across system devices (both `getSourceDeviceId` and `getDestinationDeviceId` return not-`null`'s representing id's of the current and destination devices accordingly),
- *removal* of an existing component from a device, and in turn the entire system (`getSourceDeviceId` returns a not-`null` device id where the component is currently stored and `getDestinationDeviceId` returns `null`).

Ordering of these three types of operations by the user cannot be controlled by the system implementation. Your solution, therefore, should focus on performing those transfers synchronically (i.e. if the transfer is valid, the `execute` method cannot end before the transfer's completion). As many operations can be ordered by the user *simultaneously*, the system has to provide their coordination while conforming to the following rules.

In any given moment for every component there can only be at most one transfer order. Until the transfer ends, the component is called a `transferred component` and each new call for its transfer should be treated as invalid.

The transfer itself consists of two stages and can last a significant amount (especially the second stage). Starting a transfer requires its preparation (a call to `prepare` method). Only after said preparation can the component data be sent over to the destination device (which happens by calling the `perform` method). When the data is sent (the call to `perform` ends), the transfer is completed. Both of these stages must be performed in the thread ordering the transfer.

## Safety
A transfer can either be valid or invalid. The following safety requirements apply to valid transfers only. For handling of invalid transfers see the `Error handling` section.

If the transfer represents component removal, its start is *allowed* with no further initial requirements. Otherwise, starting the transfer is allowed so long as there is a slot on the destination device for the component, in particular that slot is empty or is currently being freed and thus can be reserved. Formally, starting the transfer representing a moving or removal of the component `Cx` is *allowed* if at least one of the following statements is true:
- There is an empty slot on the destination device which has not yet been reserved by another component about to/being moved/added to that device.
- There is a component `Cy` on the destination device which is being transfereed from that device or its transfer is allowed and the slot occupied by `Cy` has not yet been reserved by another component.
- The component `Cx` belongs to a set of components being transferred such that for each component in the set its destination device contains exactly one different component from the set and none of the slots occupied by the components in the set has been reserved by a component from outside of the set.

If the transfer of `Cx` is allowed, but the destination slot is still being occupied by another component `Cy` (the last two of the above cases), then the second stage of the `Cx`'s transfer (i.e. the call to `perform` on that transfer) cannot start until the first stage of the `Cy`'s transfer is finished (i.e. the `prepare` method on that transfer ended).

Obviously, if the transfer is not allowed, it cannot be initiated, that is, neither the `prepare` nor the `perform` methods can be called.

Your solution must satisfy all of the above requirements unconditionally.

## Liveness

Good luck!

### Problem statement by
Konrad Iwanicki, MIMUW