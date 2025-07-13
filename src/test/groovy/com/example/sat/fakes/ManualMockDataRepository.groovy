package com.example.sat.fakes
import com.example.sat.DataRepository

class ManualMockDataRepository implements DataRepository {
    @Override void saveReprocessedData(String satelliteId, String data) {}
    @Override boolean wasDataSaved(String satelliteId) { return false }

    @Override
    String findDataById(String satelliteId) {
        return null
    }

    @Override
    void archiveData(String satelliteId, String dataToArchive) {

    }
}