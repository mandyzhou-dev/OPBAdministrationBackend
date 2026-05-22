package ca.openbox.shift.service;

import ca.openbox.shift.dataobject.ShiftArrangementDO;
import ca.openbox.shift.dto.PaidSickLeaveQuotaDTO;
import ca.openbox.shift.dto.ShiftCandidateDTO;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.entities.ShiftStatus;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.user.dataobject.UserDO;
import ca.openbox.user.presentation.UserPresentation;
import ca.openbox.user.repository.UserPresentationRepository;
import ca.openbox.user.repository.UserRepository;
import ca.openbox.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShiftArrangementService {
    private static final int PAID_SICK_LEAVE_QUOTA_DAYS = 5;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Vancouver");

    @Autowired
    ShiftArrangementRepository shiftArrangementRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserPresentationRepository userPresentationRepository;
    @Autowired
    EmployeePreferWorkdayBoardService employeePreferWorkdayBoardService;
    @Autowired
    UserService userService;

    public ShiftArrangement addArrangement(ShiftArrangement shiftArrangement){
        if(shiftArrangementRepository
                .getShiftArrangementDOByUsernameAndStartBetween(
                        shiftArrangement.getUsername(),
                        shiftArrangement.getStart(),shiftArrangement.getEnd()).size()==0) {
            ShiftArrangementDO insertedShiftArrangementDO = shiftArrangementRepository.save(shiftArrangement.getDO());
            return ShiftArrangement.fromDO(insertedShiftArrangementDO);
        }
        else{
            throw new DuplicateKeyException(String.format("There has been a shift exist for %s", shiftArrangement.getUsername()));
        }
    }
    public void deleteArrangement(ShiftArrangement shiftArrangement){
        shiftArrangementRepository.delete(shiftArrangement.getDO());
    }
    public ShiftArrangement modifyArrangement(ShiftArrangement shiftArrangement){
        if (shiftArrangement.getStatus() == null && shiftArrangement.getId() != null) {
            shiftArrangementRepository.findById(shiftArrangement.getId())
                    .ifPresent(existing -> shiftArrangement.setStatus(existing.getStatus()));
        }
        ShiftArrangementDO modifiedShiftArrangementDO=shiftArrangementRepository.save(shiftArrangement.getDO());
        return ShiftArrangement.fromDO(modifiedShiftArrangementDO);
    }

    public ShiftArrangement updateStatus(Integer shiftId, String newStatus, String operatorUsername) {
        ShiftArrangementDO shift = getShiftOrThrow(shiftId);
        assertManager(operatorUsername);
        assertManualStatusTarget(newStatus);

        if (ShiftStatus.PAID_SICK_LEAVE_VALUE.equals(newStatus)) {
            PaidSickLeaveQuotaDTO quota = buildPaidSickLeaveQuota(shift);
            if (quota.isProbation()) {
                throw new IllegalStateException("Employee is still in probation");
            }
            if (!quota.isCanMarkPaidSickLeave()) {
                throw new IllegalStateException("Paid sick leave quota used up");
            }
        }

        shift.setStatus(newStatus);
        return ShiftArrangement.fromDO(shiftArrangementRepository.save(shift));
    }

    public PaidSickLeaveQuotaDTO getPaidSickLeaveQuota(Integer shiftId, String operatorUsername) {
        ShiftArrangementDO shift = getShiftOrThrow(shiftId);
        assertManager(operatorUsername);
        return buildPaidSickLeaveQuota(shift);
    }

    public List<ShiftArrangement> getByGroupAndDate(String groupName, ZonedDateTime date){
        LocalDate businessDate = toBusinessDate(date);
        ZonedDateTime start = businessDate.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime end = start.plusDays(1).minusNanos(1);
        List<ShiftArrangementDO> shiftArrangementDOList = shiftArrangementRepository.getShiftArrangementDOByGroupAndStartBetween(groupName, start, end);
        return shiftArrangementDOList.stream()
                .filter(o -> !ShiftStatus.isNonWorked(o.getStatus()))
                .map(o -> ShiftArrangement.fromDO(o))
                .collect(Collectors.toList());
    }
    public List<ShiftArrangement> getByUserAndGroupAndDate(String username, String groupName, ZonedDateTime date){
        LocalDate businessDate = toBusinessDate(date);
        ZonedDateTime start = businessDate.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime end = start.plusDays(1).minusNanos(1);
        List<ShiftArrangementDO> shiftArrangementDOList = shiftArrangementRepository.getShiftArrangementDOByUsernameAndGroupAndStartBetween(username, groupName, start, end);
        return shiftArrangementDOList.stream()
                .filter(o -> !ShiftStatus.isNonWorked(o.getStatus()))
                .map(o -> ShiftArrangement.fromDO(o))
                .collect(Collectors.toList());
    }

    public List<ShiftCandidateDTO> getCandidatesByDate(ZonedDateTime date, String groupName, String role) {
        String employeeRole = (role == null || role.isBlank()) ? "tester" : role;
        LocalDate businessDate = toBusinessDate(date);
        ZonedDateTime start = businessDate.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime end = start.plusDays(1).minusNanos(1);

        Collection<UserPresentation> employees =
                userPresentationRepository.findByRolesContainingAndActiveOrderByNameAsc(employeeRole, 1);
        Set<String> preferredUsernames = new HashSet<>(employeePreferWorkdayBoardService.getPreferredEmployeesBydate(date));
        Map<String, ShiftArrangementDO> shiftsByUsername = getLowestIdShiftByUsername(start, end);

        return employees.stream()
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> toCandidate(employee, preferredUsernames, shiftsByUsername))
                .collect(Collectors.toList());
    }

    private ShiftArrangementDO getShiftOrThrow(Integer shiftId) {
        return shiftArrangementRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
    }

    private Map<String, ShiftArrangementDO> getLowestIdShiftByUsername(ZonedDateTime start, ZonedDateTime end) {
        Map<String, ShiftArrangementDO> shiftsByUsername = new HashMap<>();
        for (ShiftArrangementDO shift : shiftArrangementRepository.getShiftArrangementDOByStartBetween(start, end)) {
            shiftsByUsername.merge(shift.getUsername(), shift, this::lowerIdShift);
        }
        return shiftsByUsername;
    }

    private ShiftCandidateDTO toCandidate(UserPresentation employee,
                                          Set<String> preferredUsernames,
                                          Map<String, ShiftArrangementDO> shiftsByUsername) {
        ShiftArrangementDO existingShift = shiftsByUsername.get(employee.getUsername());
        boolean alreadyScheduled = existingShift != null;
        return new ShiftCandidateDTO(
                employee.getUsername(),
                displayName(employee),
                employee.getGroupName(),
                preferredUsernames.contains(employee.getUsername()),
                alreadyScheduled,
                alreadyScheduled ? existingShift.getId() : null,
                alreadyScheduled ? existingShift.getStatus() : null
        );
    }

    private ShiftArrangementDO lowerIdShift(ShiftArrangementDO left, ShiftArrangementDO right) {
        int leftId = left.getId() == null ? Integer.MAX_VALUE : left.getId();
        int rightId = right.getId() == null ? Integer.MAX_VALUE : right.getId();
        return leftId <= rightId ? left : right;
    }

    private String displayName(UserPresentation user) {
        if (user.getName() == null || user.getName().isBlank()) {
            return user.getUsername();
        }
        return user.getName();
    }

    private void assertManager(String operatorUsername) {
        UserDO operator = userRepository.getUserDOByUsernameAndActiveIsTrue(operatorUsername);
        if (operator == null || !isManager(operator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Manager can change shift status");
        }
    }

    private boolean isManager(UserDO operator) {
        if ("manager".equalsIgnoreCase(operator.getGroupName())) {
            return true;
        }
        String roles = operator.getRoles();
        if (roles == null || roles.isBlank()) {
            return false;
        }
        for (String role : roles.split("\\|")) {
            if ("manager".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private void assertManualStatusTarget(String status) {
        if (!ShiftStatus.isAllowedManualTarget(status)) {
            throw new IllegalArgumentException("Invalid shift status target: " + status);
        }
    }

    private PaidSickLeaveQuotaDTO buildPaidSickLeaveQuota(ShiftArrangementDO shift) {
        LocalDate targetDate = toBusinessDate(shift.getStart());
        int targetYear = targetDate.getYear();
        Set<LocalDate> usedDates = getPaidSickLeaveDates(shift.getUsername(), targetYear);
        boolean probation = userService.isInProbation(shift.getUsername());
        boolean targetDateAlreadyCounted = usedDates.contains(targetDate);

        PaidSickLeaveQuotaDTO quota = new PaidSickLeaveQuotaDTO();
        quota.setUsername(shift.getUsername());
        quota.setYear(targetYear);
        quota.setUsedDays(usedDates.size());
        quota.setQuotaDays(PAID_SICK_LEAVE_QUOTA_DAYS);
        quota.setProbation(probation);
        quota.setEligible(!probation);
        quota.setTargetDateAlreadyCounted(targetDateAlreadyCounted);
        quota.setCanMarkPaidSickLeave(!probation
                && (usedDates.size() < PAID_SICK_LEAVE_QUOTA_DAYS || targetDateAlreadyCounted));
        quota.setMessage("Used " + usedDates.size() + "/" + PAID_SICK_LEAVE_QUOTA_DAYS);
        return quota;
    }

    private Set<LocalDate> getPaidSickLeaveDates(String username, int year) {
        ZonedDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime nextYearStart = yearStart.plusYears(1);
        List<ShiftArrangementDO> paidSickLeaveShifts =
                shiftArrangementRepository.getShiftArrangementDOByUsernameAndStatusAndStartBetween(
                        username,
                        ShiftStatus.PAID_SICK_LEAVE_VALUE,
                        yearStart,
                        nextYearStart.minusNanos(1)
                );

        Set<LocalDate> dates = new HashSet<>();
        for (ShiftArrangementDO paidSickLeaveShift : paidSickLeaveShifts) {
            dates.add(toBusinessDate(paidSickLeaveShift.getStart()));
        }
        return dates;
    }

    private LocalDate toBusinessDate(ZonedDateTime time) {
        return time.withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }
}
