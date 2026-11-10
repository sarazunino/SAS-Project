package catering.businesslogic.event;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents an event in the catering system.
 */
public class Event {

    private int id;
    private String name;
    private Date dateStart;
    private Date dateEnd;
    private int participants;
    private String notes;
    private String client;
    private Client clientObj;
    private String location;
    private String state;
    private String finalDocumentation;
    private User chef;
    private Integer recurrenceId;
    private ArrayList<Service> services;

    public Event() {
        services = new ArrayList<>();
        this.state = "CREATED";
    }

    public Event(String name) {
        this();
        this.name = name;
    }

    public int getId() { return id;}
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Date getDateStart() { return dateStart; }
    public void setDateStart(Date dateStart) { this.dateStart = dateStart; }

    public Date getDateEnd() { return dateEnd; }
    public void setDateEnd(Date dateEnd) { this.dateEnd = dateEnd; }

    public User getChef() { return chef; }
    public int getChefId() { return chef != null ? chef.getId() : 0; }

    public void setChef(User chef) { this.chef = chef; }
    public void setChefId(int chefId) { this.chef = User.load(chefId); }

    public int getParticipants() { return participants; }
    public void setParticipants(int participants) { this.participants = participants; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; this.clientObj = null; }

    public Client getClientObject() {
        if (clientObj == null && client != null && !client.isBlank()) {
            clientObj = Client.loadByName(client);
        }
        return clientObj;
    }
    public void setClientObject(Client clientObj) {
        this.clientObj = clientObj;
        if (clientObj != null && clientObj.getName() != null) {
            this.client = clientObj.getName();
        }
    }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getFinalDocumentation() { return finalDocumentation; }
    public void setFinalDocumentation(String finalDocumentation) { this.finalDocumentation = finalDocumentation; }

    public Integer getRecurrenceId() { return recurrenceId; }
    public void setRecurrenceId(Integer recurrenceId) { this.recurrenceId = recurrenceId; }



    public void setField(String field, Object newValue) {
        switch (field) {
            case "name":
                if (newValue instanceof String) setName((String) newValue);
                else throw new IllegalArgumentException("Expected String for name");
                break;
            case "dateStart":
                if (newValue instanceof Date) setDateStart((Date) newValue);
                else throw new IllegalArgumentException("Expected Date for dateStart");
                break;
            case "dateEnd":
                if (newValue instanceof Date) setDateEnd((Date) newValue);
                else throw new IllegalArgumentException("Expected Date for dateEnd");
                break;
            case "participants":
                if (newValue instanceof Integer) setParticipants((Integer) newValue);
                else throw new IllegalArgumentException("Expected Integer for participants");
                break;
            case "client":
                if (newValue instanceof String) setClient((String) newValue);
                else throw new IllegalArgumentException("Expected String for client");
                break;
            case "location":
                if (newValue instanceof String) setLocation((String) newValue);
                else throw new IllegalArgumentException("Expected String for location");
                break;
            case "notes":
                if (newValue instanceof String) setNotes((String) newValue);
                else throw new IllegalArgumentException("Expected String for notes");
                break;
            default:
                throw new IllegalArgumentException("Field not supported: " + field);
        }
    }

    public boolean modifyField(String field, Object newValue) {
        if ("CREATED".equals(state)) {
            setField(field, newValue);
            if ("dateStart".equals(field) || "dateEnd".equals(field) || "location".equals(field) || "participants".equals(field)) {
                setNotes((notes != null ? notes + "\n" : "") + "Modification may impact assignments");
            }
            updateEvent();
            return true;
        }

        if ("IN_PROGRESS".equals(state)) {
            if (!"participants".equals(field)) return false;

            int newParticipants = (Integer) newValue;
            int oldParticipants = this.participants;
            double variation = (double) Math.abs(newParticipants - oldParticipants) / oldParticipants;

            if (variation > 0.3) {
                Client client = getClientObject();
                if (client != null && !client.isLoyal()) {
                    client.registerPenalty("Participants changed over 30%");
                }
            }
            setParticipants(newParticipants);
            updateEvent();
            return true;
        }

        return false;
    }

    public boolean cancelEvent() {
        if ("CANCELLED".equals(state) || "CLOSED".equals(state)) return false;

        boolean wasInProgress = "IN_PROGRESS".equals(state);

        if (services != null) {
            for (Service s : services) {
                s.removeAllStaffAssignments();
            }
        }

        this.state = "CANCELLED";

        if (wasInProgress) {
            Client c = getClientObject();
            if (c != null) {
                c.registerPenalty("Event cancelled while in progress");
            }
        }

        updateEvent();
        return true;
    } 

    public List<Event> generateRecurringInstances(Recurrence r) {
        List<Event> instances = new ArrayList<>();
        for (Date date : r.computeDates(this.getDateStart())) {
            Event clone = this.createRecurringInstance(date, r.getId());
            long offset = date.getTime() - this.getDateStart().getTime();
            if (this.getServices() != null) {
                for (Service service : this.getServices()) {
                    Service copy = service.cloneService(clone, offset);
                    clone.addService(copy);
                }
            }
            instances.add(clone);
        }
        return instances;
    }

    public Event createRecurringInstance(Date date, int recurrenceId) {
        Event copy = new Event();
        copy.setName(this.name);
        copy.setDateStart(date);
        if (this.dateEnd != null && this.dateStart != null) {
            long delta = this.dateEnd.getTime() - this.dateStart.getTime();
            copy.setDateEnd(new Date(date.getTime() + delta));
        }

        copy.setParticipants(this.participants);
        copy.setNotes(this.notes);
        copy.setClient(this.client);
        copy.setLocation(this.location);
        copy.setChef(this.chef);
        copy.setRecurrenceId(recurrenceId);
        copy.setState("CREATED");
        copy.setFinalDocumentation(null);
        return copy;
    }

    public boolean closeEvent(String notes, String finalDocumentation) {
        if (!"IN_PROGRESS".equals(state)) {
            return false;
        }

        this.notes = notes;
        this.finalDocumentation = finalDocumentation;
        this.state = "CLOSED";
        updateEvent();
        return true;
    }



    public ArrayList<Service> getServices() {
        return services;
    }

    public void setServices(ArrayList<Service> services) {
        this.services = services;
    }

    public void addService(Service service) {
        if (services == null) {
            services = new ArrayList<>();
        }
        services.add(service);
    }

    public Service addService(String name, Date date, java.sql.Time timeStart, java.sql.Time timeEnd, String location) {
        Service service = new Service();
        service.setName(name);
        service.setDate(date);
        service.setTimeStart(timeStart);
        service.setTimeEnd(timeEnd);
        service.setLocation(location);
        service.setEventId(this.id);
        addService(service);
        return service;
    }

    public void removeService(Service service) {
        if (services != null) {
            services.remove(service);
        }
    }

    public boolean containsService(Service service) {
        if (services != null) {
            return services.contains(service);
        }
        return false;
    }

  

    public void saveNewEvent() {
        String query =
            "INSERT INTO Events (name, date_start, date_end, participants, notes, client, state, final_documentation, chef_id, recurrence_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        PersistenceManager.executeUpdate(query, name,
            dateStart != null ? dateStart.toString() : null,
            dateEnd != null ? dateEnd.toString() : null,
            participants, notes,
            client, state, finalDocumentation, getChefId(), getRecurrenceId());

        this.id = PersistenceManager.getLastId();
    }

    public void updateEvent() {
        String query = "UPDATE Events SET name=?, date_start=?, date_end=?, participants=?, notes=?, client=?, state=?, final_documentation=?, chef_id=?, recurrence_id=? WHERE id=?";

        PersistenceManager.executeUpdate(query, name,
            dateStart != null ? dateStart.toString() : null,
            dateEnd != null ? dateEnd.toString() : null,
            participants, notes,
            client, state, finalDocumentation, getChefId(), getRecurrenceId(), id);
    }

    public boolean deleteEvent() {
        if (!"CREATED".equals(state)) {
            return false;
        }

        if (services != null) {
            for (Service service : new ArrayList<>(services)) {
                service.deleteService();
            }
            services.clear();
        }

        String query = "DELETE FROM Events WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }



    public static ArrayList<Event> loadAllEvents() {
        ArrayList<Event> events = new ArrayList<>();
        String query = "SELECT * FROM Events ORDER BY date_start DESC";

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> events.add(mapRow(rs)));

        if (events.isEmpty()) {
            PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
            events.clear();
            PersistenceManager.executeQuery(query,
                (ResultHandler) rs -> events.add(mapRow(rs)));
        }

        for (Event e : events) {
            e.services = Service.loadServicesForEvent(e.id);
        }

        return events;
    }

    public static Event loadById(int id) {
        return loadEventByQuery("SELECT * FROM Events WHERE id = ?", id);
    }

    public static Event loadByName(String name) {
        return loadEventByQuery("SELECT * FROM Events WHERE name = ?", name);
    }

    private static Event loadEventByQuery(String query, Object param) {
        final Event[] holder = new Event[1];

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> holder[0] = mapRow(rs), param);

        if (holder[0] == null) {
            PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
            PersistenceManager.executeQuery(query,
                (ResultHandler) rs -> holder[0] = mapRow(rs), param);
        }

        Event result = holder[0];
        if (result != null) {
            result.services = Service.loadServicesForEvent(result.id);
        }

        return result;
    }

    private static Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.id = rs.getInt("id");
        e.name = rs.getString("name");

        String ds = rs.getString("date_start");
        e.dateStart = ds != null ? Date.valueOf(ds) : null;

        String de = rs.getString("date_end");
        e.dateEnd = de != null ? Date.valueOf(de) : null;

        e.participants = rs.getInt("participants");
        e.notes = rs.getString("notes");
        e.client = rs.getString("client");
        e.state = rs.getString("state");
        e.finalDocumentation = rs.getString("final_documentation");

        int recId = rs.getInt("recurrence_id");
        e.recurrenceId = rs.wasNull() ? null : recId;

        int chefId = rs.getInt("chef_id");
        if (!rs.wasNull() && chefId > 0) {
            try { e.chef = User.load(chefId); } catch (Exception ex) { }
        }

        return e;
    }

    @Override
    public String toString() {
        return "Event [id=" + id + ", name=" + name + ", dateStart=" + dateStart +
                ", services=" + (services != null ? services.size() : 0) + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Event other = (Event) obj;
        if (this.id > 0 && other.id > 0) return this.id == other.id;

        return Objects.equals(name, other.name) && Objects.equals(dateStart, other.dateStart)
            && Objects.equals(dateEnd, other.dateEnd) && Objects.equals(client, other.client)
            && Objects.equals(location, other.location);
    }

    @Override
    public int hashCode() {
        if (this.id > 0) return Objects.hash(id);
        return Objects.hash(name, dateStart, dateEnd, client, location);
    }
}