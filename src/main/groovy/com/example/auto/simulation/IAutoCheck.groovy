package com.example.auto.simulation

import com.example.auto.dto.PhotoCollectionAction
import com.example.auto.dto.PhotoData

interface IAutoCheck extends AutoConnection, CommandModule {
    void storeActionPlan(PhotoCollectionAction manifest)
    List<PhotoData> downlinkQueuedPhotos()
}