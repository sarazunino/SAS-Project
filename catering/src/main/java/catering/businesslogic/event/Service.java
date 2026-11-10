package catering.businesslogic.event;

import java.sql.*;
import java.util.*;
import java.sql.Date;

import catering.businesslogic.menu.Menu;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a service (turno) in an event in the catering system.
 */
public class Service {

    private int id;
    private String name;
    private Date date;
    private Time timeStart;
    private Time timeEnd;
    private String location;
    private int eventId;
    private User cook;
    private Menu menu;
    private String state;
    private ArrayList<StaffAssignment> staffAssignments;

    public Service() {
        this.state = "CREATED";
        this.staffAssignments = new ArrayList<>();
    }

    public Service(String name) {
        this();
        this.name = name;
    }



    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public Time getTimeStart() { return timeStart; }
    public void setTimeStart(Time timeStart) { this.timeStart = timeStart; }

    public Time getTimeEnd() { return timeEnd; }
    public void setTimeEnd(Time timeEnd) { this.timeEnd = timeEnd; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public Integer getMenuId() { return (menu != null) ? menu.getId() : null; }
    public Menu getMenu() { return menu; }
    public void setMenu(Menu menu) { this.menu = menu; }
    public void removeMenu() { this.menu = null; }
    public ArrayList<MenuItem> getMenuItems() {
        return (this.menu != null) ? this.menu.getItems() : new ArrayList<>();
    }

    public User getCook() { return cook; }
    public int getCookId() { return cook != null ? cook.getId() : 0; }
    public void setCook(User cook) { this.cook = cook; }
    public void setCookId(int cookId) { this.cook = User.load(cookId); }

    public boolean assignCook(User cook) {
        if (cook == null) return false;
        this.cook = cook;
        return true;
    }
    public boolean removeCook() {
        if (this.cook == null) return false;
        this.cook = null;
        return true;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }



    public ArrayList<StaffAssignment> getStaffAssignments() { 
        return staffAssignments; 
    }

    public void setStaffAssignments(ArrayList<StaffAssignment> staffAssignments) {
        this.staffAssignments = staffAssignments;
    }

    public void removeAssignment(User personnel) {
        if (staffAssignments == null) return;
        staffAssignments.removeIf(a -> a.getStaffMember() != null && a.getStaffMember().equals(personnel));
    }

    public StaffAssignment findAssignment(User personnel) {
        if (staffAssignments == null) return null;
        for (StaffAssignment a : staffAssignments) {
            if (a.getStaffMember() != null && a.getStaffMember().equals(personnel)) return a;
        }
        return null;
    }

    public boolean assignStaffMember(User personnel, String role) {
        if (findAssignment(personnel) != null) return false;

        StaffAssignment as = new StaffAssignment(personnel, role);
        as.setServiceId(this.id);
        as.saveNewStaffAssignment();

        if (staffAssignments == null) staffAssignments = new ArrayList<>();
        staffAssignments.add(as);
        return true;
    }

    public boolean assignRoleToStaff(User personnel, String role) {
        StaffAssignment as = findAssignment(personnel);
        if (as == null) return false;
        if (as.getRole() != null) return false;

        as.assignRole(role);
        as.updateStaffAssignment();
        return true;
    }

    public boolean removeStaffAssignment(User personnel) {
        if ("IN_PROGRESS".equalsIgnoreCase(state)) return false;

        StaffAssignment as = findAssignment(personnel);
        if (as == null) return false;

        as.deleteStaffAssignment();
        removeAssignment(personnel);
        return true;
    }

    public boolean modifyStaffAssignmentRole(User personnel, String newRole) {
        if ("IN_PROGRESS".equalsIgnoreCase(state)) return false;

        StaffAssignment as = findAssignment(personnel);
        if (as == null) return false;

        as.setRole(newRole);
        as.updateStaffAssignment();
        return true;
    }

    public boolean removeCookIfAllowed() {
        if ("IN_PROGRESS".equalsIgnoreCase(state)) return false;
        return removeCook();
    }

   public void setField(String field, Object newValue) {
        switch (field) {
            case "name":
                if (newValue instanceof String) setName((String) newValue);
                else throw new IllegalArgumentException("Expected String for name");
                break;
            case "date":
                if (newValue instanceof Date) setDate((Date) newValue);
                else throw new IllegalArgumentException("Expected Date for date");
                break;
            case "timeStart":
                if (newValue instanceof Time) setTimeStart((Time) newValue);
                else throw new IllegalArgumentException("Expected Time for timeStart");
                break;
            case "timeEnd":
                if (newValue instanceof Time) setTimeEnd((Time) newValue);
                else throw new IllegalArgumentException("Expected Time for timeEnd");
                break;
            case "location":
                if (newValue instanceof String) setLocation((String) newValue);
                else throw new IllegalArgumentException("Expected String for location");
                break;
            case "menuId":
                if (newValue instanceof Integer) {
                    int menuId = (Integer) newValue;
                    if (menuId > 0 && this.getMenuId() != menuId) {
                        Menu menu = Menu.load(menuId);
                        if (menu != null) setMenu(menu);
                    }
                } else throw new IllegalArgumentException("Expected Integer for menuId");
                break;
            default:
                throw new IllegalArgumentException("Field not supported: " + field);
        }
    }

    public boolean modifyField(String field, Object newValue) {
        if (!"CREATED".equals(state)) return false;

        setField(field, newValue);
        updateService();
        return true;
    }

    public Service cloneService(Event event, long offset) {
        Service copy = new Service();

        copy.setName(this.name);
        copy.setDate(new Date(this.date.getTime() + offset));
        copy.setTimeStart(this.timeStart);
        copy.setTimeEnd(this.timeEnd);
        copy.setLocation(this.location);
        copy.setState("CREATED");
        copy.setEventId(event.getId());

        return copy;
    }

    public boolean cancelService() {
        if ("CANCELLED".equals(state) || "CLOSED".equals(state)) return false;

        boolean wasInProgress = "IN_PROGRESS".equals(state);
        removeAllStaffAssignments();
        this.state = "CANCELLED";

        if (wasInProgress) {
            Event event = Event.loadById(this.getEventId());
            if (event != null) {
                Client client = event.getClientObject();
                if (client != null) {
                    client.registerPenalty("Service cancelled while in progress");
                }
            }
        }
        updateService();
        return true;
    }

    public boolean proposeMenuChanges(int menuId, String dishesToAdd, String dishesToRemove) {
        if (getMenu() == null || getMenu().getId() != menuId) return false;
        if (!"CREATED".equals(state)) return false;

        ProposeChanges pc = new ProposeChanges(dishesToAdd, dishesToRemove);
        pc.setServiceId(this.getId());
        pc.setMenuId(menuId);
        pc.saveNewProposeChanges();
        return true;
    }



    public void saveNewService() {
        String query = "INSERT INTO Services (event_id, name, service_date, time_start, time_end, location, cook_id, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        PersistenceManager.executeUpdate(query,
            this.getEventId(), this.getName(),
            this.getDate() != null ? this.getDate().toString() : null,
            this.getTimeStart() != null ? this.getTimeStart().toString() : null,
            this.getTimeEnd() != null ? this.getTimeEnd().toString() : null,
            this.getLocation(), this.getCookId(), this.state);

        this.setId(PersistenceManager.getLastId());

        if (this.staffAssignments != null) {
            for (StaffAssignment a : this.staffAssignments) {
                a.setServiceId(this.id);
                a.saveNewStaffAssignment();
            }
        }
    }

    public void updateService() {
        String query =
            "UPDATE Services SET name = ?, service_date = ?, time_start = ?, time_end = ?, " +
            "location = ?, cook_id = ?, state = ?, approved_menu_id = ? WHERE id = ?";

        PersistenceManager.executeUpdate(query,
            this.getName(),
            this.getDate() != null ? this.getDate().toString() : null,
            this.getTimeStart() != null ? this.getTimeStart().toString() : null,
            this.getTimeEnd() != null ? this.getTimeEnd().toString() : null,
            this.getLocation(),
            this.getCookId(),
            this.state,
            this.menu != null ? this.menu.getId() : null,
            this.getId()
        );
    }

    public boolean deleteService() {
        if (!"CREATED".equals(state)) return false;
        PersistenceManager.executeUpdate("DELETE FROM StaffAssignments WHERE service_id = ?", this.getId());
        return PersistenceManager.executeUpdate("DELETE FROM Services WHERE id = ?", this.getId()) > 0;
    }

    public void assignMenuToService(Menu menu) {
        this.menu = menu;
        PersistenceManager.executeUpdate("UPDATE Services SET approved_menu_id = ? WHERE id = ?", menu.getId(), this.id);
    }

    public void removeMenuFromService() {
        this.menu = null;
        PersistenceManager.executeUpdate("UPDATE Services SET approved_menu_id = NULL WHERE id = ?", this.id);
    }

    public void approveMenu(Menu menu) {
        if(menu != null) setMenu(menu);
        this.menu.setInUse(true);
        this.setState("IN_PROGRESS");
        PersistenceManager.executeUpdate("UPDATE Services SET approved_menu_id = ?, state = ? WHERE id = ?", menu.getId(), this.state, this.id); 
    }

    public void removeAllStaffAssignments() {
        PersistenceManager.executeUpdate("DELETE FROM StaffAssignments WHERE service_id = ?", this.getId());
        if (staffAssignments != null) staffAssignments.clear();
    }

    public static ArrayList<Service> loadServicesForEvent(int eventId) {
        ArrayList<Service> services = new ArrayList<>();
        String query = "SELECT * FROM Services WHERE event_id = ? ORDER BY service_date, time_start";

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> services.add(mapRow(rs)), eventId);

        for (Service s : services) {
            s.staffAssignments = StaffAssignment.loadForService(s.id);
        }
        return services;
    }

    public static Service loadById(int id) {
        return loadServiceByQuery("SELECT * FROM Services WHERE id = ?", id);
    }

    public static Service loadByName(String name) {
        return loadServiceByQuery("SELECT * FROM Services WHERE name = ?", name);
    }

    private static Service loadServiceByQuery(String query, Object param) {
        final Service[] holder = new Service[1];
        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> holder[0] = mapRow(rs), param);

        if (holder[0] == null) {
            PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
            PersistenceManager.executeQuery(query,
                (ResultHandler) rs -> holder[0] = mapRow(rs), param);
        }

        if (holder[0] != null) {
            holder[0].staffAssignments = StaffAssignment.loadForService(holder[0].id);
        }
        return holder[0];
    }

    private static Service mapRow(ResultSet rs) throws SQLException {
        Service s = new Service();
        s.id = rs.getInt("id");
        s.name = rs.getString("name");
        String dateStr = rs.getString("service_date");
        s.date = dateStr != null ? Date.valueOf(dateStr) : null;
        String startStr = rs.getString("time_start");
        s.timeStart = startStr != null ? Time.valueOf(startStr) : null;
        String endStr = rs.getString("time_end");
        s.timeEnd = endStr != null ? Time.valueOf(endStr) : null;
        s.location = rs.getString("location");
        s.eventId  = rs.getInt("event_id");
        int cookId = rs.getInt("cook_id");
        if (!rs.wasNull() && cookId > 0) {
            try { s.cook = User.load(cookId); } catch (Exception ex) { }
        }
        int menuId = rs.getInt("approved_menu_id");
        if (!rs.wasNull() && menuId > 0) {
            try { s.menu = Menu.load(menuId); } catch (Exception ex) { }
        }
        String stateStr = rs.getString("state");
        s.state = (stateStr != null) ? stateStr : "CREATED";
        return s;
    }

    @Override
    public String toString() {
        return "Service [id=" + id + ", name=" + name + ", date=" + date +
               ", location=" + location + ", state=" + state +
               ", cook=" + (cook != null ? cook.getUserName() : "none") +
               ", menu=" + (menu != null ? menu.getTitle() : "none") + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Service other = (Service) obj;
        if (this.id > 0 && other.id > 0) return this.id == other.id;

        return eq(this.name, other.name) && eq(this.date, other.date)
            && eq(this.timeStart, other.timeStart) && eq(this.timeEnd, other.timeEnd)
            && eq(this.location, other.location) && eq(this.cook, other.cook)
            && eq(this.menu, other.menu)
            && (this.eventId <= 0 || other.eventId <= 0 || this.eventId == other.eventId);
    }

    @Override
    public int hashCode() {
        if (this.id > 0) return Objects.hash(id);
        return Objects.hash(name, date, timeStart, timeEnd, location, cook, menu);
    }

    private static boolean eq(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}