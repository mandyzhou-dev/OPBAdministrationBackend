package ca.openbox.shift.controller.KPI;

import ca.openbox.shift.entities.KPIRecord;
import ca.openbox.shift.service.KPI.KPIRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shift/kpi-record")
public class KPIRecordController {

    @Autowired
    private KPIRecordService kpiRecordService;

    @CrossOrigin(origins = "http://localhost:8081")
    @GetMapping
    public List<KPIRecord> getKpiRecordsByYear(@RequestParam String year) {
        return kpiRecordService.getKpiRecordsByYear(year);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PostMapping
    public KPIRecord createKpiRecord(@RequestBody KPIRecord kpiRecord) {
        return kpiRecordService.createOrUpdateKpiRecord(kpiRecord);
    }

    @CrossOrigin(origins = "http://localhost:8081")
    @PutMapping("/{id}")
    public KPIRecord updateKpiRecord(@PathVariable Long id, @RequestBody KPIRecord kpiRecord) {
        return kpiRecordService.updateKpiRecord(id, kpiRecord);
    }
}
