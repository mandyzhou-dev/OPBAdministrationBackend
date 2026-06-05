package ca.openbox.process.service;

import ca.openbox.process.dataobject.LeaveApplicationDO;
import ca.openbox.process.dataobject.LeaveApplicationProofDO;
import ca.openbox.process.dto.LeaveDateAvailabilityDTO;
import ca.openbox.process.dto.LeaveDateAvailabilityDateDTO;
import ca.openbox.process.dto.PageResponseDTO;
import ca.openbox.process.entities.LeaveApplication;
import ca.openbox.process.entities.LeaveApplicationDeleteRules;
import ca.openbox.process.repository.LeaveApplicationProofRepository;
import ca.openbox.process.repository.LeaveApplicationRepository;
import ca.openbox.process.service.components.ApplicationStatusChangeMessageQueue;
import ca.openbox.process.service.components.LeaveApplicationEmailEvent;
import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class LeaveApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");
    private static final String PENDING_STATUS = LeaveApplicationDeleteRules.PENDING_STATUS;
    private static final String SICK_LEAVE_TYPE = "SICK";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_AVAILABILITY_RANGE_DAYS = 120;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("submitTime", "start");

    @Autowired
    LeaveApplicationRepository leaveApplicationRepository;
    @Autowired
    LeaveApplicationProofRepository leaveApplicationProofRepository;
    @Autowired
    SickLeaveProofStorageService sickLeaveProofStorageService;
    @Autowired
    ApplicationHistoryAccessPolicy applicationHistoryAccessPolicy;
    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;
    Clock clock = Clock.system(BUSINESS_ZONE);

    @Transactional
    public LeaveApplication addLeaveApplication(LeaveApplication leaveApplication){
        validateLeaveApplicationDates(leaveApplication);
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.save(leaveApplication.toDO());
        LeaveApplicationProofDO proofDO = createRequiredProofRowIfSick(leaveApplicationDO);
        LeaveApplication savedApplication = LeaveApplication.fromDO(leaveApplicationDO, proofDO);
        ApplicationStatusChangeMessageQueue.put(LeaveApplicationEmailEvent.leaveSubmitted(savedApplication));
        return savedApplication;
    }

    public LeaveDateAvailabilityDTO getLeaveDateAvailability(String applicant, LocalDate from, LocalDate to) {
        String rawApplicant = requireApplicant(applicant);
        validateDateRange(from, to);
        validateAvailabilityRangeSize(from, to);

        Map<LocalDate, List<Integer>> shiftIdsByDate = getScheduledShiftIdsByBusinessDate(rawApplicant, from, to);
        List<LeaveDateAvailabilityDateDTO> dates = getBusinessDatesInclusive(from, to).stream()
                .map(date -> {
                    List<Integer> shiftIds = shiftIdsByDate.getOrDefault(date, List.of());
                    return new LeaveDateAvailabilityDateDTO(date.toString(), !shiftIds.isEmpty(), shiftIds);
                })
                .toList();

        return new LeaveDateAvailabilityDTO(
                rawApplicant,
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
        LeaveApplicationDO leaveApplicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        if (leaveApplicationDO == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        if (!canDeleteApplicationStatus(leaveApplicationDO.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application status does not support deletion");
        }
        leaveApplicationRepository.deleteById(applicationID);
    }

    public boolean canDeleteApplicationStatus(String status) {
        return LeaveApplicationDeleteRules.canDeleteStatus(status);
    }
    public List<LeaveApplication> getApplicationsByHandler(String handler){
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByCurrentHandlerContainingOrderBySubmitTimeDesc(handler);
        return enrichApplications(leaveApplicationDOList);
    }
    public List<LeaveApplication> getApplicationsByApplicant(String applicant){
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByApplicantOrderBySubmitTimeDesc(applicant);
        return enrichApplications(leaveApplicationDOList);
    }
    public List<LeaveApplication> getAllApplications(){
        List<LeaveApplicationDO> leaveApplicationDOList = leaveApplicationRepository.getLeaveApplicationDOByStatusIsNotContainingOrderBySubmitTimeDesc(PENDING_STATUS);
        return enrichApplications(leaveApplicationDOList);
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

        List<LeaveApplication> applications = enrichApplications(applicationPage.getContent());
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

    @Transactional
    public LeaveApplication uploadSickProof(Integer applicationID, String applicant, MultipartFile proof) {
        LeaveApplicationDO applicationDO = leaveApplicationRepository.getLeaveApplicationDOById(applicationID);
        if (applicationDO == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        if (!isSickLeave(applicationDO.getLeaveType())) {
            throw badRequest("Sick proof can only be uploaded for sick leave applications");
        }
        validateApplicantMatches(applicationDO, applicant);

        LeaveApplicationProofDO proofDO = getOrCreateRequiredProofRow(applicationDO);
        StoredSickLeaveProof storedProof = sickLeaveProofStorageService.store(applicationID, proof);
        ZonedDateTime now = nowInBusinessZone();
        populateProofCreatedAtIfMissing(proofDO, now);
        proofDO.setStatus("SUBMITTED");
        proofDO.setUploadedAt(now);
        proofDO.setOriginalFilename(storedProof.getOriginalFilename());
        proofDO.setStoredFilename(storedProof.getStoredFilename());
        proofDO.setContentType(storedProof.getContentType());
        proofDO.setFileSizeBytes(storedProof.getFileSizeBytes());
        proofDO.setUpdatedAt(now);
        LeaveApplicationProofDO savedProof = leaveApplicationProofRepository.save(proofDO);

        LeaveApplication updatedApplication = LeaveApplication.fromDO(applicationDO, savedProof);
        ApplicationStatusChangeMessageQueue.put(LeaveApplicationEmailEvent.sickProofUploaded(updatedApplication, storedProof.getStoredPath()));
        return updatedApplication;
    }

    private LeaveApplicationProofDO createRequiredProofRowIfSick(LeaveApplicationDO leaveApplicationDO) {
        if (!isSickLeave(leaveApplicationDO.getLeaveType()) || leaveApplicationProofRepository == null) {
            return null;
        }
        LeaveApplicationProofDO proofDO = new LeaveApplicationProofDO();
        proofDO.setApplicationId(leaveApplicationDO.getId());
        proofDO.setProofType("SICK_LEAVE_PROOF");
        proofDO.setStatus("REQUIRED");
        populateProofAuditTimestamps(proofDO);
        return leaveApplicationProofRepository.save(proofDO);
    }

    private LeaveApplicationProofDO getOrCreateRequiredProofRow(LeaveApplicationDO applicationDO) {
        Optional<LeaveApplicationProofDO> existingProof = leaveApplicationProofRepository.findById(applicationDO.getId());
        if (existingProof.isPresent()) {
            return existingProof.get();
        }
        LeaveApplicationProofDO proofDO = new LeaveApplicationProofDO();
        proofDO.setApplicationId(applicationDO.getId());
        proofDO.setProofType("SICK_LEAVE_PROOF");
        proofDO.setStatus("REQUIRED");
        populateProofAuditTimestamps(proofDO);
        return leaveApplicationProofRepository.save(proofDO);
    }

    private void populateProofAuditTimestamps(LeaveApplicationProofDO proofDO) {
        ZonedDateTime now = nowInBusinessZone();
        populateProofCreatedAtIfMissing(proofDO, now);
        proofDO.setUpdatedAt(now);
    }

    private void populateProofCreatedAtIfMissing(LeaveApplicationProofDO proofDO, ZonedDateTime now) {
        if (proofDO.getCreatedAt() == null) {
            proofDO.setCreatedAt(now);
        }
    }

    private List<LeaveApplication> enrichApplications(List<LeaveApplicationDO> applicationDOList) {
        if (applicationDOList == null || applicationDOList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, LeaveApplicationProofDO> proofsByApplicationId = getProofsByApplicationId(applicationDOList);
        return applicationDOList.stream()
                .map(applicationDO -> LeaveApplication.fromDO(applicationDO, proofsByApplicationId.get(applicationDO.getId())))
                .toList();
    }

    private Map<Integer, LeaveApplicationProofDO> getProofsByApplicationId(List<LeaveApplicationDO> applicationDOList) {
        if (leaveApplicationProofRepository == null) {
            return Collections.emptyMap();
        }
        List<Integer> applicationIds = applicationDOList.stream()
                .map(LeaveApplicationDO::getId)
                .filter(id -> id != null)
                .toList();
        if (applicationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, LeaveApplicationProofDO> proofsByApplicationId = new HashMap<>();
        leaveApplicationProofRepository.findByApplicationIdIn(applicationIds)
                .forEach(proofDO -> proofsByApplicationId.put(proofDO.getApplicationId(), proofDO));
        return proofsByApplicationId;
    }

    private void validateApplicantMatches(LeaveApplicationDO applicationDO, String applicant) {
        String normalizedRequestApplicant = normalizeApplicantForOwnershipCheck(applicant);
        String normalizedApplicationApplicant = normalizeApplicantForOwnershipCheck(applicationDO.getApplicant());
        if (normalizedRequestApplicant == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Applicant is required");
        }
        if (!normalizedRequestApplicant.equals(normalizedApplicationApplicant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Applicant does not match leave application");
        }
    }

    private String normalizeApplicantForOwnershipCheck(String applicant) {
        if (applicant == null || applicant.isBlank()) {
            return null;
        }
        return applicant.trim();
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

        if (isSickLeave(leaveApplication.getLeaveType())) {
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
        String rawApplicant = requireApplicant(applicant);
        Set<LocalDate> scheduledDates = getScheduledShiftIdsByBusinessDate(rawApplicant, startDate, endDate).keySet();
        boolean missingSchedule = getBusinessDatesInclusive(startDate, endDate).stream()
                .anyMatch(date -> !scheduledDates.contains(date));
        if (missingSchedule) {
            throw badRequest("Sick leave requires an existing scheduled shift for every selected date");
        }
    }

    private LocalDate toBusinessDate(ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }

    private ZonedDateTime nowInBusinessZone() {
        return ZonedDateTime.now(clock.withZone(BUSINESS_ZONE));
    }

    private Map<LocalDate, List<Integer>> getScheduledShiftIdsByBusinessDate(String applicant, LocalDate from, LocalDate to) {
        ZonedDateTime rangeStart = from.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime rangeEnd = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).minusNanos(1);
        List<ShiftArrangementDO> shifts = shiftArrangementRepository.getShiftArrangementDOByUsernameAndStartBetween(
                applicant,
                rangeStart,
                rangeEnd
        );
        String trimmedApplicant = applicant.trim();
        if (shifts.isEmpty() && !trimmedApplicant.isEmpty()) {
            shifts = shiftArrangementRepository.getShiftArrangementDOByTrimmedUsernameAndStartBetween(
                    trimmedApplicant,
                    rangeStart,
                    rangeEnd
            );
        }

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

    private String requireApplicant(String applicant) {
        if (applicant == null || applicant.isBlank()) {
            throw badRequest("Applicant is required");
        }
        return applicant;
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

    private boolean isSickLeave(String leaveType) {
        return leaveType != null && SICK_LEAVE_TYPE.equalsIgnoreCase(leaveType.trim());
    }
}
