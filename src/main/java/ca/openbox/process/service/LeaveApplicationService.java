package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.dto.LeaveDateAvailabilityDateDTO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.repository.LeaveApplicationRepository;
import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaveApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");
    private static final String PENDING_STATUS = "pending";
    private static final String SICK_LEAVE_TYPE = "SICK";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_AVAILABILITY_RANGE_DAYS = 120;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("submitTime", "start");

    @Autowired
    LeaveApplicationRepository leaveApplicationRepository;
    @Autowired
    ApplicationHistoryAccessPolicy applicationHistoryAccessPolicy;
    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;
    Clock clock = Clock.system(BUSINESS_ZONE);

    public LeaveApplication addLeaveApplication(LeaveApplication leaveApplication){
        validateLeaveApplicationDates(leaveApplication);
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.save(leaveApplication.toDO());
        return LeaveApplication.fromDO(leaveApplicationDO);
    }

    public LeaveDateAvailabilityDTO getLeaveDateAvailability(String applicant, LocalDate from, LocalDate to) {
        String normalizedApplicant = normalizeRequiredApplicant(applicant);
        validateDateRange(from, to);
        validateAvailabilityRangeSize(from, to);

        Map<LocalDate, List<Integer>> shiftIdsByDate = getScheduledShiftIdsByBusinessDate(normalizedApplicant, from, to);
        List<LeaveDateAvailabilityDateDTO> dates = getBusinessDatesInclusive(from, to).stream()
                .map(date -> {
                    List<Integer> shiftIds = shiftIdsByDate.getOrDefault(date, List.of());
                    return new LeaveDateAvailabilityDateDTO(date.toString(), !shiftIds.isEmpty(), shiftIds);
                })
                .toList();

        return new LeaveDateAvailabilityDTO(
                normalizedApplicant,
                from.toString(),
                to.toString(),
                BUSINESS_ZONE.getId(),
                dates
        );
    }

    public void permitApplication(Integer applicationID){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setStatus("approved");
        leaveApplicationDO.setCurrentHandler(leaveApplicationDO.getApplicant());
        leaveApplicationRepository.save(leaveApplicationDO);
    }
    public void rejectApplication(Integer applicationID, String rejectReason){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setStatus("rejected");
        leaveApplicationDO.setRejectReason(rejectReason);
        leaveApplicationDO.setCurrentHandler(leaveApplicationDO.getApplicant());
        leaveApplicationRepository.save(leaveApplicationDO);
    }
    public void deleteApplication(Integer applicationID){
        leaveApplicationRepository.deleteById(applicationID);
    }
    public List<LeaveApplication> getApplicationsByHandler(String handler){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
       // leaveApplicationRepository.getLeaveApplication
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(handler);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public List<LeaveApplication> getApplicationsByApplicant(String applicant){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
        // leaveApplicationRepository.getLeaveApplication
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc(applicant);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public List<LeaveApplication> getAllApplications(){
        List<LeaveApplication> leaveApplicationList = new ArrayList<>();
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc(PENDING_STATUS);
        for(int i = 0; i<leaveApplicationDOList.size();++i){
            leaveApplicationList.add(LeaveApplication.fromDO(leaveApplicationDOList.get(i)));
        }
        return leaveApplicationList;
    }
    public PageResponseDTO<LeaveApplication> getHistory(String employeeUsername, int page, int size, String sort, String operatorUsername){
        applicationHistoryAccessPolicy.resolveVisibility(operatorUsername);
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), toSort(sort));
        String normalizedEmployee = normalizeEmployee(employeeUsername);

        Page<LeaveApplicationDO> applicationPage;
        if (normalizedEmployee == null) {
            applicationPage = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContaining(PENDING_STATUS, pageable);
        } else {
            applicationPage = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingAndApplicant(PENDING_STATUS, normalizedEmployee, pageable);
        }

        List<LeaveApplication> applications = applicationPage.getContent().stream()
                .map(LeaveApplication::fromDO)
                .toList();
        return new PageResponseDTO<>(
                applications,
                applicationPage.getNumber(),
                applicationPage.getSize(),
                applicationPage.getTotalElements(),
                applicationPage.getTotalPages(),
                toSortParameter(pageable.getSort())
        );
    }
    public void addNoteToApplication(Integer applicationID, String note){
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        leaveApplicationDO.setNote(note);
        leaveApplicationRepository.save(leaveApplicationDO);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeEmployee(String employeeUsername) {
        if (employeeUsername == null || employeeUsername.isBlank()) {
            return null;
        }
        return employeeUsername.trim();
    }

    private Sort toSort(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "submitTime";
        String direction = parts.length > 1 ? parts[1].trim() : "desc";
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "submitTime";
            direction = "desc";
        }
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sortDirection, field);
    }

    private String toSortParameter(Sort sort) {
        Sort.Order order = sort.iterator().next();
        return order.getProperty() + "," + order.getDirection().name().toLowerCase();
    }

    private void validateLeaveApplicationDates(LeaveApplication leaveApplication) {
        if (leaveApplication == null || leaveApplication.getStart() == null || leaveApplication.getEnd() == null) {
            throw badRequest("Leave start and end are required");
        }

        LocalDate startDate = toBusinessDate(leaveApplication.getStart());
        LocalDate endDate = toBusinessDate(leaveApplication.getEnd());
        validateDateRange(startDate, endDate);
        assertNotPast(startDate, endDate);

        if (SICK_LEAVE_TYPE.equalsIgnoreCase(leaveApplication.getLeaveType())) {
            assertScheduledForSickLeave(leaveApplication.getApplicant(), startDate, endDate);
        }
    }

    private void assertNotPast(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        boolean hasPastDate = getBusinessDatesInclusive(startDate, endDate).stream()
                .anyMatch(date -> date.isBefore(today));
        if (hasPastDate) {
            throw badRequest("Leave date cannot be before today");
        }
    }

    private void assertScheduledForSickLeave(String applicant, LocalDate startDate, LocalDate endDate) {
        String normalizedApplicant = normalizeRequiredApplicant(applicant);
        Set<LocalDate> scheduledDates = getScheduledShiftIdsByBusinessDate(normalizedApplicant, startDate, endDate).keySet();
        boolean missingSchedule = getBusinessDatesInclusive(startDate, endDate).stream()
                .anyMatch(date -> !scheduledDates.contains(date));
        if (missingSchedule) {
            throw badRequest("Sick leave requires an existing scheduled shift for every selected date");
        }
    }

    private LocalDate toBusinessDate(ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }

    private Map<LocalDate, List<Integer>> getScheduledShiftIdsByBusinessDate(String applicant, LocalDate from, LocalDate to) {
        ZonedDateTime rangeStart = from.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime rangeEnd = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).minusNanos(1);
        List<ShiftArrangementDO> shifts = shiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(
                applicant,
                rangeStart,
                rangeEnd
        );

        Map<LocalDate, List<Integer>> shiftIdsByDate = new LinkedHashMap<>();
        shifts.stream()
                .filter(shift -> shift.getStart() != null)
                .sorted(Comparator.comparing(ShiftArrangementDO::getId, Comparator.nullsLast(Integer::compareTo)))
                .forEach(shift -> {
                    LocalDate date = toBusinessDate(shift.getStart());
                    shiftIdsByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(shift.getId());
                });
        return shiftIdsByDate;
    }

    private List<LocalDate> getBusinessDatesInclusive(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            dates.add(date);
            date = date.plusDays(1);
        }
        return dates;
    }

    private String normalizeRequiredApplicant(String applicant) {
        if (applicant == null || applicant.isBlank()) {
            throw badRequest("Applicant is required");
        }
        return applicant.trim();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw badRequest("Date range is required");
        }
        if (to.isBefore(from)) {
            throw badRequest("Date range end cannot be before start");
        }
    }

    private void validateAvailabilityRangeSize(LocalDate from, LocalDate to) {
        if (from.plusDays(MAX_AVAILABILITY_RANGE_DAYS - 1L).isBefore(to)) {
            throw badRequest("Date range cannot exceed 120 days");
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
