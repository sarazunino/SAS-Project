package catering.businesslogic.event;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.user.User;

/**
 * EventManager handles all operations related to events and services in the
 * CatERing system.
 * It manages event creation, modification, and deletion, as well as service
 * management and menu assignments for services.
 */
public class EventManager {

    private ArrayList<EventReceiver> eventReceivers;
    private Event selectedEvent;
    private Service currentService;

    public EventManager() {
        eventReceivers = new ArrayList<>();
    }

    public void addEventReceiver(EventReceiver receiver) {
        if (receiver != null && !eventReceivers.contains(receiver)) {
            eventReceivers.add(receiver);
        }
    }

    public void removeEventReceiver(EventReceiver receiver) {
        eventReceivers.remove(receiver);
    }



    public ArrayList<Event> getEvents() {
        return Event.loadAllEvents();
    }

    public void setSelectedService(int serviceId) {
        if (selectedEvent != null && selectedEvent.getServices() != null) {
            for (Service si : selectedEvent.getServices()) {
                if (si.getId() == serviceId) {
                    currentService = si;
                    return;
                }
            }
        }

    }

    public void setCurrentService(Service service) {
        this.currentService = service;
    }
    public Service getCurrentService() {
        return this.currentService;
    }

    public Event getSelectedEvent() {
        return selectedEvent;
    }
    public void setSelectedEvent(Event event) {
        this.selectedEvent = event;
    }

    public void selectEvent(Event event) {
        this.selectedEvent = event;
        this.currentService = null;
    }

    //1
    public Event createEvent(String name, String location, Date dateStart, Date dateEnd, int participants, String notes, String client) 
            throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = new Event();
        event.setName(name);
        event.setLocation(location);
        event.setDateStart(dateStart);
        event.setDateEnd(dateEnd);
        event.setParticipants(participants);
        if(notes != null) event.setNotes(notes);
        event.setClient(client);
        Client resolvedClient = Client.loadByName(client);
        if (resolvedClient != null) event.setClientObject(resolvedClient);
        event.setState("CREATED");
        event.setFinalDocumentation(null);

        event.saveNewEvent();
        notifyEventCreated(event);

        selectEvent(event);

        return event;
    }

    //1a.1
    public void setupRecurrentEvent(int eventId, int frequency, Date endDate, Integer repetitions) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");

        Recurrence r = new Recurrence(frequency, endDate, repetitions);
        r.saveNewRecurrence();

        event.setRecurrenceId(r.getId());
        event.updateEvent();

        if (selectedEvent.getId() == event.getId()) {
            selectedEvent = event;
        }

        notifyEventModified(event);

        for (Event instance : event.generateRecurringInstances(r)) {
            instance.saveNewEvent();
            notifyEventCreated(instance);
        }
    }

    //1b.1
    public void modifyRecurrenceFrequency(int eventId, int frequency, Date endDate, Integer repetitions) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event master = Event.loadById(eventId);
        if (master == null || master.getRecurrenceId() == null) throw new UseCaseLogicException("No recurrent event found");

        Recurrence r = Recurrence.loadById(master.getRecurrenceId());
        if (r == null) throw new UseCaseLogicException("Recurrence not found");

        r.setFrequency(frequency);
        r.setEndDate(endDate);
        r.setNumRepetitions(repetitions);
        r.updateRecurrence();

        if (selectedEvent != null && selectedEvent.getId() == master.getId()) {
            selectedEvent = master;
        }

        for (Event created : r.generateMissingInstances(master)) {
            notifyEventCreated(created);
        }

        for (Event deleted : r.removeObsoleteInstances(master)) {
            if (selectedEvent != null && selectedEvent.getId() == deleted.getId()) {
                selectedEvent = null;
                currentService = null;
            }
            notifyEventDeleted(deleted);
        }
    }

    //1c.1
    public boolean deleteEvent(int eventId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event eventToDelete = Event.loadById(eventId);
        if (eventToDelete == null) throw new UseCaseLogicException("Event not found");

        boolean deleted = eventToDelete.deleteEvent();
        if (!deleted) throw new UseCaseLogicException("Event cannot be deleted");

        if (selectedEvent != null && selectedEvent.getId() == eventId) {
            selectedEvent = null;
            currentService = null;
        }

        notifyEventDeleted(eventToDelete);

        return true;
    }

    // 1c.1b.1
    public boolean deleteRecurrentEventInstance(int eventId, boolean allInstances) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null || event.getRecurrenceId() == null) throw new UseCaseLogicException("No recurrent event selected");

        Recurrence r = Recurrence.loadById(event.getRecurrenceId());
        if (r == null) throw new UseCaseLogicException("Recurrence not found");

        if (allInstances) {
            ArrayList<Event> instances = r.loadInstances();

            boolean deleted = r.deleteRecurrence();
            if (!deleted) throw new UseCaseLogicException("Recurrence cannot be deleted");

            for (Event e : instances) {
                if ("CREATED".equals(e.getState())) {
                    if (selectedEvent != null && selectedEvent.getId() == e.getId()) {
                        selectedEvent = null;
                        currentService = null;
                    }
                    notifyEventDeleted(e);
                }
            }
            return true;
        } else {
            boolean deleted = event.deleteEvent();
            if (!deleted) return false;
            if (selectedEvent != null && selectedEvent.getId() == event.getId()) {
                selectedEvent = null;
                currentService = null;
            }
            notifyEventDeleted(event);
            return true;
        }
    }

    //1d.1
    public boolean cancelEvent(int eventId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");

        boolean cancelled = event.cancelEvent();
        if (!cancelled) throw new UseCaseLogicException("Event cannot be cancelled");

        if (selectedEvent != null && selectedEvent.getId() == event.getId()) {
            selectedEvent = event;
        }
        notifyEventModified(event);
        return true;
    }

    //1d.1b.1
    public boolean cancelRecurrentEventInstance(int eventId, boolean allInstances) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null || event.getRecurrenceId() == null) throw new UseCaseLogicException("No recurrent event selected");

        Recurrence r = Recurrence.loadById(event.getRecurrenceId());
        if (r == null) throw new UseCaseLogicException("Recurrence not found");

        if (allInstances) {
            for (Event e : r.loadInstances()) {
                if (e.cancelEvent()) {
                    if (selectedEvent != null && e != null && selectedEvent.getId() == e.getId()) {
                        selectedEvent = e;
                    }
                    notifyEventModified(e);
                }
            }
            return true;
        } else {
            if (event.cancelEvent()) {
                if (selectedEvent != null && event != null && selectedEvent.getId() == event.getId()) {
                    selectedEvent = event;
                }
                notifyEventModified(event);
                return true;
            }
            return false;
        }
    }

    //1e.1
    public void modifyEvent(int eventId, String field, Object newValue) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");

        boolean modified = event.modifyField(field, newValue);
        if (!modified) throw new UseCaseLogicException("Cannot modify event");

        if (selectedEvent != null && selectedEvent.getId() == event.getId()) {
            selectedEvent = event;
        }
        notifyEventModified(event);
    }
   
    //1e.1b.1
    public void modifyRecurrentEventInstance(int eventId, String field, Object newValue, boolean allInstances) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");
        if (event.getRecurrenceId() == null) throw new UseCaseLogicException("No recurrent event selected");

        Recurrence r = Recurrence.loadById(event.getRecurrenceId());
        if (r == null) throw new UseCaseLogicException("Recurrence not found");

        if (allInstances) {
            for (Event e : r.loadInstances()) {
                if (e.modifyField(field, newValue)) {
                    if (selectedEvent != null && selectedEvent.getId() == e.getId()) {
                        selectedEvent = e;
                    }
                    notifyEventModified(e);
                }
            }
        } else {
            if (!event.modifyField(field, newValue)) throw new UseCaseLogicException("Cannot modify event");

            if (selectedEvent != null && selectedEvent.getId() == event.getId()) {
                selectedEvent = event;
            }
            notifyEventModified(event);
        }
    }

    //2
    public Service createService(String name, Date date, Time timeStart, Time timeEnd, String location) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");
        if (selectedEvent == null) throw new UseCaseLogicException("Cannot create service: no event selected");

        Service service = new Service();
        service.setName(name);
        service.setDate(date);
        service.setTimeStart(timeStart);
        service.setTimeEnd(timeEnd);
        service.setLocation(location);
        service.setEventId(selectedEvent.getId());
        service.setState("CREATED");
        service.saveNewService();
        notifyServiceCreated(service);

        selectedEvent.addService(service);
        this.currentService = service;

        return service;
    }

    //2a.1
    public boolean deleteService(int serviceId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");

        Service serviceToDelete = findServiceById(serviceId);
        if (serviceToDelete == null) throw new UseCaseLogicException("Service not found");

        if(!serviceToDelete.deleteService()) throw new UseCaseLogicException("Service cannot be deleted");;
        selectedEvent.removeService(serviceToDelete);

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = null;
        }
        notifyServiceDeleted(serviceToDelete);

        return true;
    }

    //2b.1
    public boolean cancelService(int serviceId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");

        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");

        if (!service.cancelService()) throw new UseCaseLogicException("Service cannot be cancelled");
        notifyServiceModified(service);

        return true;
    }

    //2c.1
    public Service modifyService(int serviceId, String field, Object newValue) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");

        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");

        boolean modified = service.modifyField(field, newValue);
        if (!modified) throw new UseCaseLogicException("Cannot modify service");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }
        notifyServiceModified(service);
        return service;
    }

    //3
    public void assignChef(int eventId, User chef) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");
        
        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");
        if (chef == null || !chef.hasRole(User.Role.CHEF)) throw new UseCaseLogicException("No chef provided");
        if (event.getChef() != null) throw new UseCaseLogicException("Event already has a chef");

        event.setChef(chef);
        event.updateEvent();
        if (selectedEvent != null && event != null && selectedEvent.getId() == event.getId()) {
            selectedEvent = event;
        }
        notifyEventModified(event);
    }

    //4
    public StaffAssignment assignStaff(User personnel, int serviceId, String role) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");

        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");
        if (personnel == null || !personnel.hasRole(User.Role.SERVIZIO))
            throw new UseCaseLogicException("Personnel must have the SERVIZIO role to be assigned as staff");

        boolean assigned = service.assignStaffMember(personnel, role);
        if (!assigned) throw new UseCaseLogicException("Personnel is already assigned to this service");

        StaffAssignment as = service.findAssignment(personnel);

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }
        notifyServiceModified(service);
        return as;
    }

    //4a.1
    public StaffAssignment bookStaff(User personnel, int serviceId) throws UseCaseLogicException {
        return assignStaff(personnel, serviceId, null);
    }

    //4a.2
    public void assignStaffRole(User personnel, int serviceId, String role) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");
        if (personnel == null || !personnel.hasRole(User.Role.SERVIZIO))
            throw new UseCaseLogicException("Personnel must have the SERVIZIO role to be assigned as staff");

        boolean assigned = service.assignRoleToStaff(personnel, role);
        if (!assigned) throw new UseCaseLogicException("Cannot assign role: assignment not found or already has a role");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }

        notifyServiceModified(service);
    }

    //4b.1
    public void removeStaff(User personnel, int serviceId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");
        if (personnel == null || !personnel.hasRole(User.Role.SERVIZIO))
            throw new UseCaseLogicException("Personnel must have the SERVIZIO role to be assigned as staff");

        boolean removed = service.removeStaffAssignment(personnel);
        if (!removed) throw new UseCaseLogicException("Cannot remove staff: assignment not found or service in progress");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }

        notifyServiceModified(service);
    }

    //4c.1
    public void modifyStaffRole(User personnel, int serviceId, String newRole) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");
        if (personnel == null || !personnel.hasRole(User.Role.SERVIZIO))
            throw new UseCaseLogicException("Personnel must have the SERVIZIO role to be assigned as staff");

        boolean modified = service.modifyStaffAssignmentRole(personnel, newRole);
        if (!modified) throw new UseCaseLogicException("Cannot modify role: assignment not found or service in progress");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }

        notifyServiceModified(service);
    }

    //5
    public void assignCook(User cook, int serviceId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");
        if (cook == null || !cook.hasRole(User.Role.CUOCO)) throw new UseCaseLogicException("No cook provided");

        service.assignCook(cook);
        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }
        notifyServiceModified(service);
    }

    //5a.1
    public void removeCook(int serviceId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        if (service.getEventId() != selectedEvent.getId()) throw new UseCaseLogicException("Service does not belong to the selected event");

        boolean removed = service.removeCookIfAllowed();
        if (!removed) throw new UseCaseLogicException("Cannot remove cook: no cook assigned or service in progress");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }
        notifyServiceModified(service);
    }

    //6
    public void approveMenu(int serviceId, int menuId) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");
        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");
        Menu menu = Menu.load(menuId);
        if (menu == null) throw new UseCaseLogicException("Menu not found");
        if (service.getMenu() != null && service.getMenu().isInUse()) throw new UseCaseLogicException("Menu already approved");

        service.approveMenu(menu);

        selectedEvent.setState("IN_PROGRESS");
        selectedEvent.updateEvent();

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }

        notifyServiceModified(service);
        notifyEventModified(selectedEvent);
        notifyMenuAssigned(service, menu);
    }

    //6a.1
    public void proposeMenuChanges(int serviceId, int menuId, String dishesToAdd, String dishesToRemove) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        if (selectedEvent == null) throw new UseCaseLogicException("No event selected");

        Service service = findServiceById(serviceId);
        if (service == null) throw new UseCaseLogicException("Service not found");

        Menu menu = Menu.load(menuId);
        if (menu == null) throw new UseCaseLogicException("Menu not found");

        boolean proposed = service.proposeMenuChanges(menuId, dishesToAdd, dishesToRemove);
        if (!proposed) throw new UseCaseLogicException("Menu not associated to the selected service");

        if (currentService != null && currentService.getId() == serviceId) {
            currentService = service;
        }
    }

    //7
    public void closeEvent(int eventId, String notes, String finalDocumentation) throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) throw new UseCaseLogicException("Actor must be an organizer");

        Event event = Event.loadById(eventId);
        if (event == null) throw new UseCaseLogicException("Event not found");

        boolean closed = event.closeEvent(notes, finalDocumentation);
        if (!closed) throw new UseCaseLogicException("Event cannot be closed: it must be IN_PROGRESS");
        if (selectedEvent != null && event != null && selectedEvent.getId() == event.getId()) {
            selectedEvent = event;
        }
        notifyEventModified(event);
    }


    
    private Service findServiceById(int serviceId) {
        if (selectedEvent == null || selectedEvent.getServices() == null) return null;

        for (Service s : selectedEvent.getServices()) {
            if (s.getId() == serviceId) return s;
        }
        return null;
    }


    private void notifyEventCreated(Event event) {
        for (EventReceiver r : eventReceivers) r.updateEventCreated(event);
    }

    private void notifyEventModified(Event event) {
        for (EventReceiver r : eventReceivers) r.updateEventModified(event);
    }

    private void notifyEventDeleted(Event event) {
        for (EventReceiver r : eventReceivers) r.updateEventDeleted(event);
    }

    private void notifyServiceCreated(Service service) {
        for (EventReceiver r : eventReceivers) r.updateServiceCreated(selectedEvent, service);
    }

    private void notifyServiceModified(Service service) {
        for (EventReceiver r : eventReceivers) r.updateServiceModified(service);
    }

    private void notifyServiceDeleted(Service service) {
        for (EventReceiver r : eventReceivers) r.updateServiceDeleted(service);
    }

    private void notifyMenuAssigned(Service service, Menu menu) {
        for (EventReceiver r : eventReceivers) r.updateMenuAssigned(service, menu);
    }

    private void notifyMenuRemoved(Service service) {
        for (EventReceiver r : eventReceivers) r.updateMenuRemoved(service);
    }
}