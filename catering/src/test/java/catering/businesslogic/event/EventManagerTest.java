package catering.businesslogic.event;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Time;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the gestire-evento DSDs.
 */
class EventManagerTest {

    private CatERing app;

    @BeforeEach
    void setUp() throws UseCaseLogicException {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
        app.getUserManager().fakeLogin("Giovanni");
    }

    @Nested
    class CreateManageAndCloseEvent {

        @Test
        void testScenarioCreateManageAndCloseEvent() throws UseCaseLogicException {
            Event event = app.getEventManager().createEvent("Wedding Rossi", "Villa Bianca", Date.valueOf("2026-09-15"), Date.valueOf("2026-09-15"),
                    120, "Garden ceremony", "Mario Rossi");

            assertNotNull(event);
            assertEquals("CREATED", event.getState());

            Service service = app.getEventManager().createService("Lunch", Date.valueOf("2026-09-15"), Time.valueOf("12:00:00"), Time.valueOf("15:00:00"),
                    "Banquet Hall");

            assertNotNull(service);

            User chef = User.load("Antonio");
            app.getEventManager().assignChef(event.getId(), chef);

            User staff = User.load("Marco");
            User cook = User.load("Luca");
            app.getEventManager().assignStaff(staff, service.getId(), "Waiter");
            app.getEventManager().assignCook(cook, service.getId());

            Service serviceReloaded = Service.loadById(service.getId());

            assertEquals(1, serviceReloaded.getStaffAssignments().size());
            assertEquals("Waiter", serviceReloaded.getStaffAssignments().get(0).getRole());
            assertNotNull(serviceReloaded.getCook());
            assertEquals("Luca", serviceReloaded.getCook().getUserName());

            app.getUserManager().fakeLogin("Antonio");
            Menu menu = app.getMenuManager().createMenu("Wedding Menu");
            app.getUserManager().fakeLogin("Giovanni");

            app.getEventManager().setCurrentService(service);

            app.getEventManager().approveMenu(service.getId(), menu.getId());

            Service serviceApproved = Service.loadById(service.getId());
            assertEquals("IN_PROGRESS", serviceApproved.getState());

            Event inProgress = Event.loadById(event.getId());
            assertEquals("IN_PROGRESS", inProgress.getState());
            assertNotNull(inProgress.getChef());
            assertEquals("Antonio", inProgress.getChef().getUserName());

            app.getEventManager().closeEvent(event.getId(), "Everything went well", "Rossi_2026.pdf");
            Event closed = Event.loadById(event.getId());

            assertEquals("CLOSED", closed.getState());
            assertEquals("Everything went well", closed.getNotes());
            assertEquals("Rossi_2026.pdf", closed.getFinalDocumentation());
        }
    }

    @Nested
    class ModifyParticipants {

        @Test
        void testScenarioModifyParticipants() throws UseCaseLogicException {
            Event event = app.getEventManager().createEvent("Ongoing Event","Congress Palace",Date.valueOf("2026-10-10"),Date.valueOf("2026-10-10"),
            100, null, "Initech Corp");

            Service service = app.getEventManager().createService("Gala Dinner", Date.valueOf("2026-10-10"), Time.valueOf("19:00:00"), Time.valueOf("23:00:00"),
            "Main Hall");

            User chef = User.load("Antonio");
            app.getEventManager().assignChef(event.getId(), chef);

            app.getUserManager().fakeLogin("Antonio");
            Menu menu = app.getMenuManager().createMenu("Gala Menu");

            app.getUserManager().fakeLogin("Giovanni");
            app.getEventManager().setCurrentService(service);
            app.getEventManager().approveMenu(service.getId(), menu.getId());

            Event progress = Event.loadById(event.getId());

            assertEquals("IN_PROGRESS", progress.getState());

            app.getEventManager().modifyEvent(event.getId(), "participants", 200);

            Event updated = Event.loadById(event.getId());

            assertEquals(200, updated.getParticipants());
            assertNotNull(updated.getClientObject());
            assertEquals("Initech Corp", updated.getClientObject().getName());
            assertFalse(updated.getClientObject().getPenalties().isEmpty());
        }
    }
}