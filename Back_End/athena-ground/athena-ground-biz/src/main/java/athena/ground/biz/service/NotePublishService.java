package athena.ground.biz.service;

import athena.ground.biz.domain.dto.NoteSubmitDTO;

/**
 * 笔记发布服务：负责笔记落库
 */
public interface NotePublishService {

    Long publish(NoteSubmitDTO noteSubmitDTO);
}
