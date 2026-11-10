package catering.businesslogic.event;

import java.sql.Date;
import java.sql.Time;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a recurrence of an event in the catering system.
 */
public class Recurrence {

    private int id;
    private int frequency;
    private Date endDate;
    private Integer numRepetitions;

    public Recurrence() {
    }

    public Recurrence(int frequency, Date endDate, Integer numRepetitions) {
        this.frequency = frequency;
        this.endDate = endDate;
        this.numRepetitions = numRepetitions;
    }



    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public Integer getNumRepetitions() { return numRepetitions; }
    public void setNumRepetitions(Integer numRepetitions) { this.numRepetitions = numRepetitions; }

    public ArrayList<Date> computeDates(Date start) {
        ArrayList<Date> dates = new ArrayList<>();

        if (numRepetitions != null) {
            for (int i = 0; i < numRepetitions; i++) {
                dates.add(addPeriods(start, i));
            }
        } else if (endDate != null) {
            int i = 0;
            Date current = addPeriods(start, i);

            while (!current.after(endDate)) {
                dates.add(current);
                i++;
                current = addPeriods(start, i);
            }
        }

        return dates;
    }

    public Date addPeriods(Date base, int multiplier) {
        if (base == null) return null;
        if (multiplier == 0) return base;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(new java.util.Date(base.getTime()));

        int amount = frequency * multiplier;
        cal.add(java.util.Calendar.DAY_OF_YEAR, amount);

        return new Date(cal.getTimeInMillis());
    }

    public ArrayList<Event> generateMissingInstances(Event master) {
        ArrayList<Event> existing = loadInstances();
        ArrayList<Date> dates = computeDates(master.getDateStart());
        ArrayList<Event> created = new ArrayList<>();

        for (Date date : dates) {
            boolean alreadyExists = existing.stream().anyMatch(e -> date.equals(e.getDateStart()));
            if (!alreadyExists) {
                Event instance = master.createRecurringInstance(date, this.id);
                instance.saveNewEvent();

                long offset = date.getTime() - master.getDateStart().getTime();
                if (master.getServices() != null) {
                    for (Service service : master.getServices()) {
                        Service copy = service.cloneService(instance, offset);
                        copy.saveNewService();
                        instance.addService(copy);
                    }
                }
                created.add(instance);
            }
        }
        return created;
    }

    public ArrayList<Event> removeObsoleteInstances(Event master) {
        ArrayList<Event> existing = loadInstances();
        ArrayList<Date> dates = computeDates(master.getDateStart());
        ArrayList<Event> deleted = new ArrayList<>();

        for (Event event : existing) {
            boolean stillValid = dates.contains(event.getDateStart());
            if (event.getId() != master.getId() && !stillValid && "CREATED".equals(event.getState())) {
                event.deleteEvent();
                deleted.add(event);
            }
        }
        return deleted;
    }



    public void saveNewRecurrence() {
        String query = "INSERT INTO Recurrences (frequency, end_date, num_repetitions) VALUES (?, ?, ?)";
        PersistenceManager.executeUpdate(query, frequency, endDate, numRepetitions);
        this.id = PersistenceManager.getLastId();
    }

    public void updateRecurrence() {
        String query = "UPDATE Recurrences SET frequency=?, end_date=?, num_repetitions=? WHERE id=?";
        PersistenceManager.executeUpdate(query, frequency, endDate, numRepetitions, id);
    }

    public boolean deleteRecurrence() {
        ArrayList<Event> instances = loadInstances();

        for (Event event : instances) {
            if ("CREATED".equals(event.getState())) {
                event.deleteEvent();
            }
        }

        String query = "DELETE FROM Recurrences WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }

    public static Recurrence loadById(int id) {
        final Recurrence[] holder = new Recurrence[1];
        String query = "SELECT * FROM Recurrences WHERE id = ?";

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> holder[0] = mapRow(rs), id);

        return holder[0];
    }

    private static Recurrence mapRow(ResultSet rs) throws SQLException {
        Recurrence r = new Recurrence();
        r.id = rs.getInt("id");
        r.frequency = rs.getInt("frequency");
        Date end = rs.getDate("end_date");
        r.endDate = rs.wasNull() ? null : end;
        int numRep = rs.getInt("num_repetitions");
        r.numRepetitions = rs.wasNull() ? null : numRep;

        return r;
    }

    public ArrayList<Event> loadInstances() {
        ArrayList<Event> instances = new ArrayList<>();
        String query = "SELECT id FROM Events WHERE recurrence_id = ?";

        PersistenceManager.executeQuery(query, (ResultHandler) rs -> instances.add(Event.loadById(rs.getInt("id"))), this.id);

        return instances;
    }

    @Override
    public String toString() {
        return "Recurrence [id=" + id + ", frequency=" + frequency +
                ", endDate=" + endDate + ", numRepetitions=" + numRepetitions + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Recurrence other = (Recurrence) obj;
        if (this.id > 0 && other.id > 0) return this.id == other.id;

        return frequency == other.frequency
            && Objects.equals(endDate, other.endDate)
            && Objects.equals(numRepetitions, other.numRepetitions);
    }

    @Override
    public int hashCode() {
        if (this.id > 0) return Objects.hash(id);
        return Objects.hash(frequency, endDate, numRepetitions);
    }
}