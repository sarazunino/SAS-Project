package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Time;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

/**
 * Tests for {@link Event}: in-memory aggregate behaviour and the static
 * loaders against the seeded SQLite database.
 */
class EventTest {

    @BeforeAll
    static void initializeDatabase() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
    }

    @Nested
    class Aggregate {

        private Event event;
        private Service first;
        private Service second;

        @BeforeEach
        void setUp() {
            event = new Event("Test Event");
            first = new Service();
            first.setId(1);
            second = new Service();
            second.setId(2);
        }

        @Test
        void testName_SetInConstructor_IsReadable() {
            assertEquals("Test Event", event.getName());
        }

        @Test
        void testId_SetExplicitly_IsReadable() {
            event.setId(42);
            assertEquals(42, event.getId());
        }

        @Test
        void testChef_SetExplicitly_IsReadableAndExposesId() {
            User chef = new User();
            chef.setId(7);

            event.setChef(chef);

            assertEquals(chef, event.getChef());
            assertEquals(7, event.getChefId());
        }

        @Test
        void testServices_FreshEvent_IsEmpty() {
            assertTrue(event.getServices().isEmpty());
        }

        @Test
        void testAddService_RecordsContainment() {
            event.addService(first);

            assertTrue(event.containsService(first));
            assertFalse(event.containsService(second));
        }

        @Test
        void testRemoveService_DropsOnlyTheTargetedService() {
            event.addService(first);
            event.addService(second);

            event.removeService(first);

            assertFalse(event.containsService(first));
            assertTrue(event.containsService(second));
        }

        @Test
        void testDates_SetExplicitly_AreReadable() {
            Date day = Date.valueOf("2024-05-29");

            event.setDateStart(day);
            event.setDateEnd(day);

            assertEquals(day, event.getDateStart());
            assertEquals(day, event.getDateEnd());
        }

        @Test
        void testAddServiceWithDetails_AttachesServiceToEvent() {
            Service created = event.addService("Pranzo", Date.valueOf("2024-05-29"), Time.valueOf("12:00:00"),
                    Time.valueOf("15:00:00"), "Sala A");

            assertNotNull(created);
            assertEquals(1, event.getServices().size());
            assertEquals("Pranzo", created.getName());
            assertEquals("Sala A", created.getLocation());
            assertEquals(event.getId() == 0 ? 0 : event.getId(), created.getEventId());
        }

        @Test
        void testCancelAndCloseEvent_TransitionStatesAndUpdateFields() {
            event.setLocation("Sala Principale");
            event.setNotes("Nota iniziale");

            assertTrue(event.cancelEvent());
            assertEquals("CANCELLED", event.getState());

            event.setState("IN_PROGRESS");
            assertTrue(event.closeEvent("Nota finale", "Doc finale"));
            assertEquals("CLOSED", event.getState());
            assertEquals("Nota finale", event.getNotes());
            assertEquals("Doc finale", event.getFinalDocumentation());
        }
    }

    @Nested
    class StaticLoaders {

        @Test
        void testLoadAllEvents_ReturnsSeededEvents() {
            List<Event> events = Event.loadAllEvents();

            assertNotNull(events);
            assertFalse(events.isEmpty(), "the seed script must populate at least one event");

            Event sample = events.get(0);
            assertNotNull(sample.getName());
            assertNotNull(sample.getDateStart());
            assertNotNull(sample.getChef());
            assertNotNull(sample.getServices());
        }

        @Test
        void testLoadById_RoundTripsTheSameEvent() {
            Event sample = Event.loadAllEvents().get(0);

            Event loaded = Event.loadById(sample.getId());

            assertNotNull(loaded);
            assertEquals(sample.getId(), loaded.getId());
            assertEquals(sample.getName(), loaded.getName());
        }

        @Test
        void testLoadByName_FindsEventByExactName() {
            Event sample = Event.loadAllEvents().get(0);

            Event loaded = Event.loadByName(sample.getName());

            assertNotNull(loaded);
            assertEquals(sample.getName(), loaded.getName());
        }
    }
}
