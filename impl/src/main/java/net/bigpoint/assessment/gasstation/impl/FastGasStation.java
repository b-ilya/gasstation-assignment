package net.bigpoint.assessment.gasstation.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

/**
 * Thread safe implementation of the GasStation interface.
 * Main challenges to make this GasStation faster:
 *  - Avoid waiting if not one pump with requested fuel after other whaiting requests;
 *  - Distribute clients between several pumps with one GasType;
 *  - Make efficient and fair FIFO queue for waiting clients;
 *  - Ignore rare cases that would require complex logic:
 *     - When there is queue for a pump and another one is added;
 *     - When pump selection would result in different number of sales;
 * Algorithm for pump selection among pumps with enoung fuel of requested type:
 *  - Select the one with less clients in line;
 *  - If several select the one with more fuel.
 */
public class FastGasStation implements GasStation {

  /**
   * Entity to store some additional data about the pump and handle waiting clients.
   */
  private static class GasPumpQueue {
    /** Provides safe waiting mechanism for clients from multiple threads. */
    private final BlockingQueue<GasPump> pump = new ArrayBlockingQueue<>(1, true);
    /** Helps better distribute clients between pumps. */
    private volatile int length = 0;
    /** Indicates amount of fuel after all waiting clients is served. */
    private volatile double availableAmount;
    /**
     * Initializes the queue.
     */
    public GasPumpQueue(GasPump pump) {
      this.pump.add(pump);
      availableAmount = pump.getRemainingAmount();
    }
  }

  /** Pumps added to GasStation to avoid duplication. */
  private final Set<GasPump> pumps = new HashSet<>();
  // assuming that price does not change after setup,
  //   othervwise need to synchronize checkout and set price.
  private final Map<GasType, Double> price = new EnumMap<>(GasType.class);
  private final Map<GasType, List<GasPumpQueue>> queues = new EnumMap<>(GasType.class);
  private final Object statsLock = new Object();
  private volatile int salesCount = 0;
  private volatile int cancelNotEnough = 0;
  private volatile int cancelTooExpensive = 0;
  private volatile double revenue = 0.0;

  /**
   * Initializes queues map.
   */
  public FastGasStation() {
    for (var type : GasType.values()) {
      queues.put(type, new ArrayList<>());
    }
  }

  @Override
  public void addGasPump(GasPump pump) {
    if (pump == null) {
      throw new IllegalArgumentException("pump cannot be null");
    }
    if (pump.getGasType() == null) {
      throw new IllegalArgumentException("pump's gas type can't be null");
    }
    if (pump.getRemainingAmount() < 0.0) {
      throw new IllegalArgumentException("pump gas amount muste be non-negative");
    }
    if (pumps.contains(pump)) {
      //possible check
      //throw new IllegalArgumentException("pump can only be added once");
      return;
    }
    pumps.add(pump);

    var type = pump.getGasType();
    queues.get(type).add(new GasPumpQueue(pump));
  }

  // Important:
  // This method violates incapculation and provides unprotected access to pump.
  // Do not use returned GasPumps' methods.
  // To solve this would  need to return protected versions of the pumps added to GasStation.
  // Bit it may break interface contract.
  @Override
  public Collection<GasPump> getGasPumps() {
    return new HashSet<>(pumps);
  }

  @Override
  public double buyGas(GasType type, double amountInLiters, double maxPricePerLiter)
      throws NotEnoughGasException, GasTooExpensiveException {
    if (type == null) {
      throw new IllegalArgumentException("GasType cannot be null");
    }
    if (amountInLiters <= 0.0) {
      throw new IllegalArgumentException("requested amount must be positive");
    }
    if (maxPricePerLiter <= 0.0) {
      throw new IllegalArgumentException("price must be positive number");
    }

    if (queues.get(type).isEmpty()) {
      synchronized (statsLock) {
        cancelNotEnough++;
      }
      throw new NotEnoughGasException();
    }
    if (price.get(type) == null) {
      throw new IllegalStateException("price is not set for this type of gas");
    }
    if (price.get(type) > maxPricePerLiter) {
      synchronized (statsLock) {
        cancelTooExpensive++;
      }
      throw new GasTooExpensiveException();
    }

    GasPumpQueue selectedQueue = null;
    synchronized(queues.get(type)) { // grab lock to find suitable pump and reserve fuel.
      for (GasPumpQueue queue : queues.get(type)) {
        if (queue.availableAmount > amountInLiters) {
          if (selectedQueue == null || selectedQueue.length > queue.length
              || (selectedQueue.length == queue.length
              && selectedQueue.availableAmount < queue.availableAmount)) {
            selectedQueue = queue;
          }
        }
      }
      if (selectedQueue == null) {
        synchronized (statsLock) {
          cancelNotEnough++;
        }
        throw new NotEnoughGasException();
      }
      // reserve fuel and queue position;
      selectedQueue.length++;
      selectedQueue.availableAmount -= amountInLiters;
    } // release (queues.get(type)) lock for other threads while waiting in line and pumping.
    GasPump pump = null;
    while (pump == null) {
      try {
        pump = selectedQueue.pump.take();
      } catch (InterruptedException ignored) {
      }
    }
    pump.pumpGas(amountInLiters);
    synchronized (queues.get(type)) { // grab lock to exit line after pumping
      selectedQueue.length--;
    }
    selectedQueue.pump.add(pump);
    double cost = price.get(type) * amountInLiters;
    synchronized (statsLock) { // grab lock to checkout
      salesCount++;
      revenue += cost;
    }
    return cost;
  }

  @Override
  public double getRevenue() {
    return revenue;
  }

  @Override
  public int getNumberOfSales() {
    return salesCount;
  }

  @Override
  public int getNumberOfCancellationsNoGas() {
    return cancelNotEnough;
  }

  @Override
  public int getNumberOfCancellationsTooExpensive() {
    return cancelTooExpensive;
  }

  @Override
  public double getPrice(GasType type) {
    if (price.get(type) == null) {
      throw new IllegalStateException("Price was was not set");
    }
    return price.get(type);
  }

  @Override
  public void setPrice(GasType type, double typePrice) {
    if (type == null) {
      throw new IllegalArgumentException("type must be not null");
    }
    if (typePrice <= 0.0) {
      throw new IllegalArgumentException("gas price must be positive");
    }
    //possible check
    //if (price.get(type) != null) {
    //  throw new IllegalStateException("price cannot change once set");
    //}
    price.put(type, typePrice);
  }
}
