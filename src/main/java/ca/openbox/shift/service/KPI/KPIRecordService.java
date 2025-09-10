package ca.openbox.shift.service.KPI;

import ca.openbox.shift.dataobject.KPIRecordDO;
import ca.openbox.shift.entities.KPIRecord;
import ca.openbox.shift.repository.KPIRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class KPIRecordService {

    @Autowired
    private KPIRecordRepository kpiRecordRepository;


    public List<KPIRecord> getKpiRecordsByYear(String year) {
        String yearPattern = year + "%";
        List<KPIRecordDO> doList = kpiRecordRepository.findByYear(yearPattern);

        return doList.stream()
                .map(KPIRecord::fromDO)
                .collect(Collectors.toList());
    }

    public KPIRecord createOrUpdateKpiRecord(KPIRecord kpiRecord) {
        KPIRecordDO savedDO = kpiRecordRepository.save(kpiRecord.getDO());
        return KPIRecord.fromDO(savedDO);
    }

    public KPIRecord updateKpiRecord(Long id, KPIRecord newRecord) {
        Optional<KPIRecordDO> existingRecordOpt = kpiRecordRepository.findById(id);
        if (existingRecordOpt.isPresent()) {
            KPIRecordDO existingDO = existingRecordOpt.get();
            existingDO.setTitle(newRecord.getTitle());
            existingDO.setExpected(newRecord.getExpected());
            existingDO.setActual(newRecord.getActual());

            KPIRecordDO updatedDO = kpiRecordRepository.save(existingDO);
            return KPIRecord.fromDO(updatedDO);
        }
        throw new RuntimeException("KPI Record with id " + id + " not found");
    }
}
