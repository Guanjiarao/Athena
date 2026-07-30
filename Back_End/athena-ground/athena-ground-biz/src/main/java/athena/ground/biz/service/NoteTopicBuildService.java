package athena.ground.biz.service;

import athena.ground.biz.mq.event.NoteTopicBuildEvent;

public interface NoteTopicBuildService {

    void rebuildTopicsForNote(NoteTopicBuildEvent event);
}
