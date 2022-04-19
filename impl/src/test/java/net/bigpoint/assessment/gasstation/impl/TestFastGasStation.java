package net.bigpoint.assessment.gasstation.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TestFastGasStation {

  GasStation station;

  private void assertStats(double revenue, int sales, int cancelNotEnough, int cancelExpensive) {
    assertEquals(revenue, station.getRevenue(), 0.001,
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
    @DisplayName("empty pump collection is returned")
    public void testPumpsIsEmpty() {
      assertTrue(station.getGasPumps().isEmpty());
    }

    @Test
    @DisplayName("pumps added")
    public void testAddPumps() {
      var regularPump1 = new GasPump(GasType.REGULAR, 10.0);
      var regularPump2 = new GasPump(GasType.REGULAR, 15.0);
      var superPump = new GasPump(GasType.SUPER, 20.0);

      station.addGasPump(regularPump1);
      assertEquals(1, station.getGasPumps().size());
      assertTrue(station.getGasPumps().contains(regularPump1));

      station.addGasPump(regularPump2);
      assertEquals(2, station.getGasPumps().size());
      assertTrue(station.getGasPumps().contains(regularPump2));

      station.addGasPump(superPump);
      assertEquals(3, station.getGasPumps().size());
      assertTrue(station.getGasPumps().contains(superPump));
      assertTrue(station.getGasPumps().contains(regularPump1));
      assertTrue(station.getGasPumps().contains(regularPump2));
    }

    @Test
    @DisplayName("stats is clear")
    public void testEmptyStats() {
      assertStats(0.0, 0, 0, 0);
    }

    @Test
    @DisplayName("modifications to getGasPumps result ignored")
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
      assertStats(0.0, 0, 1, 0);
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

    @Test
    @DisplayName("getPrice fails if not set")
    public void testGetPriceFails() {
      assertThrows(IllegalStateException.class,
          () -> station.getPrice(GasType.DIESEL));
    }

    @Test
    @DisplayName("set price")
    public void testSetPrice() {
      station.setPrice(GasType.DIESEL, 10.0);
      assertEquals(10.0, station.getPrice(GasType.DIESEL), 0.001);
    }

    @Test
    @DisplayName("update price")
    public void testUpdatePrice() {
      station.setPrice(GasType.DIESEL, 10.0);
      assertEquals(10.0, station.getPrice(GasType.DIESEL), 0.001);
      station.setPrice(GasType.DIESEL, 5.0);
      assertEquals(5.0, station.getPrice(GasType.DIESEL), 0.001);
    }

    @Test
    @DisplayName("set price fails")
    public void testSetPriceFails() {
      assertThrows(IllegalArgumentException.class,
          () -> station.setPrice(null, 10.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.setPrice(GasType.DIESEL, 0.0));
      assertThrows(IllegalArgumentException.class,
          () -> station.setPrice(GasType.DIESEL, -1.0));
    }

    @Disabled
    @Test
    @DisplayName("update of price fails")
    public void testChangePriceFails() {
        assertThrows(IllegalStateException.class,
            () -> station.setPrice(GasType.DIESEL, 15.0));
    }

    // Has different number of pumps by type: DIESEL: 0, SUPER: 1, REGULAR: 2
    @Nested
    @DisplayName("When pumps added")
    public class PumpsSet {

      private GasPump regularPump1;
      private GasPump regularPump2;
      private GasPump superPump;

      @BeforeEach
      public void setPumps() {
        regularPump1 = new GasPump(GasType.REGULAR, 10.0);
        regularPump2 = new GasPump(GasType.REGULAR, 15.0);
        superPump = new GasPump(GasType.SUPER, 20.0);
        station.addGasPump(regularPump1);
        station.addGasPump(regularPump2);
        station.addGasPump(superPump);
      }

      @Test
      @DisplayName("correct pumps returned")
      public void testGetGasPumps() throws Exception {
        var pumps = station.getGasPumps();
        assertEquals(3, pumps.size());
        assertTrue(pumps.contains(regularPump1));
        assertTrue(pumps.contains(regularPump2));
        assertTrue(pumps.contains(superPump));
      }

      @Test
      @DisplayName("modifications to getGasPumps result ignored")
      public void testPumpsImmutability() throws Exception {
        assertEquals(3, station.getGasPumps().size());
        var pumpsCopy = station.getGasPumps();
        pumpsCopy.add(new GasPump(GasType.DIESEL, 100.0));
        assertEquals(3, station.getGasPumps().size());
      }

      @Test
      @DisplayName("buyGas fails as price is not set")
      public void testBuyGasFails() throws Exception {
        assertThrows(IllegalStateException.class,
            () -> station.buyGas(GasType.REGULAR, 10.0, 2.0));
      }

      // Price is set for REGULAR, DIESEL gas types
      // Pumps: 2x REGULAR: 10 + 15, 1x SUPER: 20
      @Nested
      @DisplayName("When setup complete")
      public class SetupComplete {

        @BeforeEach
        public void setPumps() {
          station.setPrice(GasType.REGULAR, 0.8);
          station.setPrice(GasType.DIESEL, 1.0);
        }

        @Test
        @DisplayName("GetPrice returns correct values")
        public void testGetPrice() {
          assertEquals(0.8, station.getPrice(GasType.REGULAR), 0.001);
          assertEquals(1.0, station.getPrice(GasType.DIESEL), 0.001);
          assertThrows(IllegalStateException.class,
              () -> station.getPrice(GasType.SUPER));
        }

        @Test
        @DisplayName("Fails if asked more than any pump has")
        public void testFailBuyLots() throws Exception {
          assertThrows(NotEnoughGasException.class,
              () -> station.buyGas(GasType.REGULAR, 25, 2));
          assertStats(0.0, 0, 1, 0);
        }

        @Test
        @DisplayName("Fails if asked more than left in any pump")
        public void testNotEnoughLeft() throws Exception {
          station.buyGas(GasType.REGULAR, 0.1, 2);
          assertStats(0.08, 1, 0, 0);
          assertThrows(NotEnoughGasException.class,
              () -> station.buyGas(GasType.REGULAR, 15, 2));
          assertStats(0.08, 1, 1, 0);
        }

        @Test
        @DisplayName("Fails if gas is more expensive than requested max")
        public void testTooExpensive() throws Exception {
          assertThrows(GasTooExpensiveException.class,
              () -> station.buyGas(GasType.REGULAR, 1, 0.5));
          assertStats(0.0, 0, 0, 1);
        }

        @Test
        @DisplayName("buy gas in small amounts")
        public void testSmallBuyGas() throws Exception {
          assertEquals(0.4, station.buyGas(GasType.REGULAR, 0.5, 1), 0.001);
          assertStats(0.4, 1, 0, 0);
        }

        @Test
        @Timeout(value = 150, unit = TimeUnit.MILLISECONDS)
        @DisplayName("concurrent pumping")
        public void testBuyGasConcurrent() throws Exception {
          var executor = Executors.newFixedThreadPool(2);

          executor.submit(() -> {
              assertEquals(0.8, station.buyGas(GasType.REGULAR, 1, 1), 0.001);
              return 0;
          });
          executor.submit(() -> {
              assertEquals(0.8, station.buyGas(GasType.REGULAR, 1, 1), 0.001);
              return 0;
          });

          executor.shutdown();
          executor.awaitTermination(150, TimeUnit.MILLISECONDS);
          assertStats(1.6, 2, 0, 0);
        }

      }
    }
  }
}

