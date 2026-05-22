package ca.openbox.shift.controller;

import ca.openbox.shift.dto.BatchCreateShiftByDateDTO;
import ca.openbox.shift.dto.PaidSickLeaveQuotaDTO;
import ca.openbox.shift.dto.ShiftArrangementDTO;
import ca.openbox.shift.dto.ShiftCandidateDTO;
import ca.openbox.shift.dto.ShiftStatusUpdateDTO;
import ca.openbox.shift.entities.ShiftArrangement;
import ca.openbox.shift.repository.ShiftArrangementRepository;
import ca.openbox.shift.service.ShiftArrangementService;
import ca.openbox.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/shift/shiftarrangement")
public class ShiftArrangementController {

    @Autowired
    ShiftArrangementService shiftArrangementService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    private ShiftArrangementRepository shiftArrangementRepository;

    @PutMapping
    public ShiftArrangement putArrangement(@RequestBody ShiftArrangementDTO shiftArrangementDTO){
        ShiftArrangement shiftArrangement = ShiftArrangement.fromDTO(shiftArrangementDTO);
        return shiftArrangementService.addArrangement(shiftArrangement);
    }
    @CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
    @PutMapping("/batchCreateByDate")
    public void batchCreateByDate(@RequestBody BatchCreateShiftByDateDTO batchCreateShiftByDateDTO, HttpServletRequest request){
        System.out.println(request.getUserPrincipal());
        for(int i = 0;i<batchCreateShiftByDateDTO.getUsernames().size();++i){
            ShiftArrangement shiftArrangement = new ShiftArrangement();
            shiftArrangement.setUsername(batchCreateShiftByDateDTO.getUsernames().get(i));
            shiftArrangement.setStart(batchCreateShiftByDateDTO.getWorkDate().withFixedOffsetZone().withHour(9).withMinute(30).withSecond(0));
            shiftArrangement.setEnd(batchCreateShiftByDateDTO.getWorkDate().withFixedOffsetZone().withHour(18).withMinute(0).withSecond(0));
            shiftArrangement.setStatus("active");
            shiftArrangement.setGroupName(batchCreateShiftByDateDTO.getGroupName());
            shiftArrangementService.addArrangement(shiftArrangement);
        }
    }
    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/deleteCurrentShift")
    public void deleteCurrentShift(@RequestBody ShiftArrangementDTO shiftArrangementDTO){
        ShiftArrangement shiftArrangement =ShiftArrangement.fromDTO(shiftArrangementDTO);
        System.out.println(shiftArrangement.toString());
        shiftArrangementService.deleteArrangement(shiftArrangement);
    }
    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/modifyCurrentShift")
    public ShiftArrangement modifyArrangement(@RequestBody ShiftArrangementDTO shiftArrangementDTO){
        ShiftArrangement shiftArrangement = ShiftArrangement.fromDTO(shiftArrangementDTO);
        return shiftArrangementService.modifyArrangement(shiftArrangement);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PatchMapping("/{id}/status")
    public ShiftArrangement updateStatus(@PathVariable Integer id, @RequestBody ShiftStatusUpdateDTO shiftStatusUpdateDTO) {
        return shiftArrangementService.updateStatus(
                id,
                shiftStatusUpdateDTO.getStatus(),
                shiftStatusUpdateDTO.getOperatorUsername()
        );
    }

    @CrossOrigin(origins = "http://localhost:8081", allowCredentials = "true")
    @GetMapping("/candidatesByDate")
    public List<ShiftCandidateDTO> getCandidatesByDate(@RequestParam(value = "date") ZonedDateTime date,
                                                       @RequestParam(value = "groupName", required = false) String groupName,
                                                       @RequestParam(value = "role", required = false, defaultValue = "tester") String role) {
        return shiftArrangementService.getCandidatesByDate(date, groupName, role);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping("/{id}/paid-sick-leave-quota")
    public PaidSickLeaveQuotaDTO getPaidSickLeaveQuota(@PathVariable Integer id,
                                                       @RequestParam String operatorUsername) {
        return shiftArrangementService.getPaidSickLeaveQuota(id, operatorUsername);
    }
}
