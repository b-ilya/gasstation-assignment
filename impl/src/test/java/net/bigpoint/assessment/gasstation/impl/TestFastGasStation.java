package net.bigpoint.assessment.gasstation.impl;

import static org.junit.jupiter.api.Assertions.*;

import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class TestFastGasStation {

  GasStation station;

  private void assertStats(double revenue, int sales, int cancelNotEnough, int cancelExpensive) {
    assertEquals(revenue, station.getRevenue(),
        () -> "revenue mismatch: expected: " + revenue
            + ", actual: " + station.getNumberOfSales());
    assertEquals(sales, station.getNumberOfSales(),
        () -> "numberOfSales mismatch: expected: " + sales
            + ", actual: " + station.getNumberOfSales());
    assertEquals(cancelNotEnough, station.getNumberOfCancellationsNoGas(),
        () -> "numberOfCancellationsNoGas mismatch: expected: " + cancelNotEnough
            + ", actual: " + station.getNumberOfCancellationsNoGas());
    assertEquals(cancelExpensive, station.getNumberOfCancellationsTooExpensive(),
        () -> "numberOfCancellationsTooExpensive mismatch: expected: " + cancelExpensive
            + ", actual: " + station.getNumberOfCancellationsTooExpensive());
  }

  @Test
  @DisplayName("No argument constructor")
  public void testConstuctor() {
    new FastGasStation();
  }

  @Nested
  @DisplayName("When not initialized")
  public class NotInit {

    @BeforeEach
    public void create() {
      station = new FastGasStation();
    }

    @Test
    @DisplayName("Empty pump collection is returned")
    public void testPumpsIsEmpty() {
      assertTrue(station.getGasPumps().isEmpty());
    }

    @Test
    @DisplayName("stats is clear")
    public void testEmptyStats() {
      assertStats(0.0, 0, 0, 0);
    }

    @Test
    @DisplayName("Modifications to getGasPumps result ignored")
    public void testPumpsIncapsulation() {
      var pumpsCopy = station.getGasPumps();
      pumpsCopy.add(new GasPump(GasType.DIESEL, 100.0));
      assertTrue(station.getGasPumps().isEmpty());
    }

    @Test
    @DisplayName("buyGas fails if not initialized or on incorrect input")
    public void testBuyGasFails() throws Exception {
      assertThrows(NotEnoughGasException.class,
          () -> station.buyGas(GasType.DIESEL, 100.0, 10.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.buyGas(null, 1.0, 1.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.buyGas(GasType.DIESEL, 0, 1.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.buyGas(GasType.DIESEL, -1.0, 1.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.buyGas(GasType.DIESEL, 1.0, 0));
      assertThrows(IllegalArgumentException.class,
          () -> station.buyGas(GasType.DIESEL, 1.0, -1.0));
    }
  }
}
