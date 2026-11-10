package catering.businesslogic.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a staff assignment in an event in the catering system.
 */
public class StaffAssignment {

    private int id;
    private int serviceId;
    private User staffMember;
    private String role; 

    public StaffAssignment() {
    }

    public StaffAssignment(User staffMember) {
        this.staffMember = staffMember;
        this.role = null;
    }

    public StaffAssignment(User staffMember, String role) {
        this.staffMember = staffMember;
        this.role = role;
    }



    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getServiceId() { return serviceId; }
    public void setServiceId(int serviceId) { this.serviceId = serviceId; }

    public User getStaffMember() { return staffMember; }
    public void setStaffMember(User staffMember) { this.staffMember = staffMember; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }




    public void assignRole(String role) {
        this.role = role;
    }

    public void clearRole() {
        this.role = null;
    }

    public boolean hasRole() {
        return this.role != null && !this.role.isBlank();
    }

    public void saveNewStaffAssignment() {
        String query = "INSERT INTO StaffAssignments (service_id, staff_id, role) VALUES (?, ?, ?)";
        PersistenceManager.executeUpdate(query, serviceId, staffMember != null ? staffMember.getId() : null, role);
        this.id = PersistenceManager.getLastId();
    }

    public void updateStaffAssignment() {
        String query = "UPDATE StaffAssignments SET role = ? WHERE id = ?";
        PersistenceManager.executeUpdate(query, role, id);
    }

    public boolean deleteStaffAssignment() {
        String query = "DELETE FROM StaffAssignments WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
    }

    public static StaffAssignment loadById(int assignmentId) {
        final StaffAssignment[] holder = new StaffAssignment[1];
        String query = "SELECT * FROM StaffAssignments WHERE id = ?";

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> holder[0] = mapRow(rs), assignmentId);

        return holder[0];
    }

    public static ArrayList<StaffAssignment> loadForService(int serviceId) {
        ArrayList<StaffAssignment> list = new ArrayList<>();
        String query = "SELECT * FROM StaffAssignments WHERE service_id = ?";

        PersistenceManager.executeQuery(query,
            (ResultHandler) rs -> list.add(mapRow(rs)), serviceId);

        return list;
    }

    private static StaffAssignment mapRow(ResultSet rs) throws SQLException {
        StaffAssignment a = new StaffAssignment();
        a.id = rs.getInt("id");
        a.serviceId = rs.getInt("service_id");
        a.role = rs.getString("role");

        int staffId = rs.getInt("staff_id");
        if (!rs.wasNull()) {
            a.staffMember = User.load(staffId);
        }
        return a;
    }

    @Override
    public String toString() {
        return "StaffAssignment [id=" + id + ", serviceId=" + serviceId + ", staffMember="
                + (staffMember != null ? staffMember.getUserName() : "null") + ", role=" + role + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StaffAssignment other = (StaffAssignment) obj;
        return id == other.id && serviceId == other.serviceId && Objects.equals(role, other.role)
                && Objects.equals(staffMember, other.staffMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serviceId, role, staffMember);
    }
}