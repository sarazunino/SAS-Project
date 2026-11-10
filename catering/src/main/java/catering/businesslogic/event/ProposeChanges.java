package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents proposed changes to a menu used in a service.
 */
public class ProposeChanges {

    private int id;
    private int serviceId;
    private int menuId;
    private String dishesToAdd;
    private String dishesToRemove;

    public ProposeChanges() {}

    public ProposeChanges(String dishesToAdd, String dishesToRemove) {
        this.dishesToAdd = dishesToAdd;
        this.dishesToRemove = dishesToRemove;
    }
    
    public ProposeChanges(int serviceId, int menuId, String dishesToAdd, String dishesToRemove) {
        this.serviceId = serviceId;
        this.menuId = menuId;
        this.dishesToAdd = dishesToAdd;
        this.dishesToRemove = dishesToRemove;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getServiceId() { return serviceId; }
    public void setServiceId(int serviceId) { this.serviceId = serviceId; }

    public int getMenuId() { return menuId; }
    public void setMenuId(int menuId) { this.menuId = menuId; }

    public String getDishesToAdd() { return dishesToAdd; }
    public void setDishesToAdd(String dishesToAdd) { this.dishesToAdd = dishesToAdd; }

    public String getDishesToRemove() { return dishesToRemove; }
    public void setDishesToRemove(String dishesToRemove) { this.dishesToRemove = dishesToRemove; }



    public void saveNewProposeChanges() {
        String query =
            "INSERT INTO ProposeChanges " +
            "(service_id, menu_id, dishes_to_add, dishes_to_remove) " +
            "VALUES (?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query,
            serviceId, menuId, dishesToAdd, dishesToRemove);
        this.id = PersistenceManager.getLastId();
    }

    public void updateProposeChanges() {
        String query =
            "UPDATE ProposeChanges " +
            "SET dishes_to_add = ?, dishes_to_remove = ? WHERE id = ?";
        PersistenceManager.executeUpdate(query, dishesToAdd, dishesToRemove, id);
    }

    public boolean deleteProposeChanges() {
        return PersistenceManager.executeUpdate(
            "DELETE FROM ProposeChanges WHERE id = ?", id) > 0;
    }

    public static ProposeChanges loadById(int id) {
        final ProposeChanges[] holder = new ProposeChanges[1];
        PersistenceManager.executeQuery(
            "SELECT * FROM ProposeChanges WHERE id = ?",
            (ResultHandler) rs -> holder[0] = mapRow(rs), id);
        return holder[0];
    }

  public static ArrayList<ProposeChanges> loadForService(int serviceId) {
        ArrayList<ProposeChanges> list = new ArrayList<>();
        PersistenceManager.executeQuery(
            "SELECT * FROM ProposeChanges WHERE service_id = ?",
            (ResultHandler) rs -> list.add(mapRow(rs)), serviceId);
        return list;
    }

    private static ProposeChanges mapRow(ResultSet rs) throws SQLException {
        ProposeChanges pc = new ProposeChanges();
        pc.id = rs.getInt("id");
        pc.serviceId = rs.getInt("service_id");
        pc.menuId = rs.getInt("menu_id");
        pc.dishesToAdd = rs.getString("dishes_to_add");
        pc.dishesToRemove = rs.getString("dishes_to_remove");
        return pc;
    }

    @Override
    public String toString() {
        return "ProposeChanges [id=" + id + ", serviceId=" + serviceId + ", menuId=" + menuId +
               ", dishesToAdd=" + dishesToAdd + ", dishesToRemove=" + dishesToRemove + "]";
    }
}