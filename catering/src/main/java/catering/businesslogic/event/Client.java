package catering.businesslogic.event;

import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a client in the catering system.
 */
public class Client {

    private int id;
    private String name;
    private boolean isLoyal;
    private List<String> penalties;

    public Client() {
        this("", false);
    }

    public Client(String name, boolean isLoyal) {
        this.name = name;
        this.isLoyal = isLoyal;
        this.penalties = new ArrayList<>();
    }

    

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isLoyal() { return isLoyal; }
    public void setLoyal(boolean loyal) { isLoyal = loyal; }

    public List<String> getPenalties() { return penalties; }
    

    
    public int registerPenalty(String reason) {
        if (penalties == null) penalties = new ArrayList<>();
        long ts = System.currentTimeMillis();
        penalties.add(reason + "@" + ts);

        if (this.id > 0) {
            PersistenceManager.executeUpdate(
                "INSERT INTO ClientPenalties (client_id, reason, timestamp) VALUES (?, ?, ?)",
                this.id, reason, ts
            );
        }

        return penalties.size();
    }


    
    public void saveNewClient() {
        PersistenceManager.executeUpdate("INSERT INTO Clients (name, is_loyal) VALUES (?, ?)", name, isLoyal ? 1 : 0);
        this.id = PersistenceManager.getLastId();
    }

    public void updateClient() {
        PersistenceManager.executeUpdate("UPDATE Clients SET name = ?, is_loyal = ? WHERE id = ?", name, isLoyal ? 1 : 0, id);
    }

    public static Client loadById(int id) {
        final Client[] holder = new Client[1];
        PersistenceManager.executeQuery(
            "SELECT * FROM Clients WHERE id = ?",
            new ResultHandler() {
                @Override
                public void handle(ResultSet rs) throws SQLException {
                    holder[0] = mapRow(rs);
                }
            }, id
        );
        if (holder[0] != null) {
            holder[0].penalties = loadPenalties(id);
        }
        return holder[0];
    }

    public static Client loadByName(String name) {
        final Client[] holder = new Client[1];
        PersistenceManager.executeQuery(
            "SELECT * FROM Clients WHERE name = ?",
            new ResultHandler() {
                @Override
                public void handle(ResultSet rs) throws SQLException {
                    holder[0] = mapRow(rs);
                }
            }, name
        );
        if (holder[0] != null) {
            holder[0].penalties = loadPenalties(holder[0].id);
        }
        return holder[0];
    }

    public static ArrayList<Client> loadAll() {
        ArrayList<Client> list = new ArrayList<>();
        PersistenceManager.executeQuery(
            "SELECT * FROM Clients ORDER BY name",
            new ResultHandler() {
                @Override
                public void handle(ResultSet rs) throws SQLException {
                    list.add(mapRow(rs));
                }
            }
        );
        for (Client c : list) {
            c.penalties = loadPenalties(c.id);
        }
        return list;
    }

    private static List<String> loadPenalties(int clientId) {
        List<String> list = new ArrayList<>();
        PersistenceManager.executeQuery(
            "SELECT reason, timestamp FROM ClientPenalties WHERE client_id = ? ORDER BY timestamp",
            new ResultHandler() {
                @Override
                public void handle(ResultSet rs) throws SQLException {
                    list.add(rs.getString("reason") + "@" + rs.getLong("timestamp"));
                }
            }, clientId
        );
        return list;
    }

    private static Client mapRow(ResultSet rs) throws SQLException {
        Client c = new Client();
        c.id = rs.getInt("id");
        c.name = rs.getString("name");
        c.isLoyal = rs.getInt("is_loyal") == 1;
        c.penalties = new ArrayList<>();
        return c;
    }

    @Override
    public String toString() {
        return "Client [id=" + id + ", name=" + name +
               ", loyal=" + isLoyal + ", penalties=" + penalties.size() + "]";
    }
}