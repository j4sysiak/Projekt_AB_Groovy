package com.example.auto.fakes
import com.example.auto.DataRepository

class ManualMockDataRepository implements DataRepository {
    @Override void saveReprocessedData(String autoIdId, String data) {}
    @Override boolean wasDataSaved(String autoIdId) { return false }

    @Override
    String findDataById(String autoIdId) {
        return null
    }

    @Override
    void archiveData(String autoIdId, String dataToArchive) {

    }
}