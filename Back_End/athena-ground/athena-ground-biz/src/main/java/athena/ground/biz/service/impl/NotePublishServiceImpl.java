package athena.ground.biz.service.impl;

import athena.ground.biz.domain.dataobject.NoteBasicDO;
import athena.ground.biz.domain.dataobject.NoteContentDO;
import athena.ground.biz.domain.dataobject.NoteCountDO;
import athena.ground.biz.domain.dataobject.NoteDO;
import athena.ground.biz.domain.dto.NoteSubmitDTO;
import athena.ground.biz.domain.mapper.NoteBasicDOMapper;
import athena.ground.biz.domain.mapper.NoteContentDOMapper;
import athena.ground.biz.domain.mapper.NoteCountDOMapper;
import athena.ground.biz.domain.mapper.NoteDOMapper;
import athena.ground.biz.service.NotePublishService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 笔记发布服务实现：负责笔记落库
 */
@Slf4j
@Service
public class NotePublishServiceImpl implements NotePublishService {

    @Resource
    private NoteBasicDOMapper noteBasicMapper;

    @Resource
    private NoteDOMapper noteMapper;

    @Resource
    private NoteContentDOMapper noteContentDOMapper;

    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(NoteSubmitDTO noteSubmitDTO) {
        NoteBasicDO noteBasicDO = new NoteBasicDO();
        BeanUtils.copyProperties(noteSubmitDTO, noteBasicDO);
        noteBasicDO.setStatus((byte) 0);
        int basicInsert = noteBasicMapper.insertSelective(noteBasicDO);

        Long noteId = noteBasicDO.getNoteId();
        log.info("插入NoteBasicDO成功, noteId={}", noteId);

        NoteDO noteDO = new NoteDO();
        BeanUtils.copyProperties(noteSubmitDTO, noteDO);
        noteDO.setId(noteId);
        List<String> imgUris = noteSubmitDTO.getImgUrls();
        if (imgUris != null && !imgUris.isEmpty()) {
            noteDO.setImgUrls(String.join(",", imgUris));
        }
        int detailInsert = noteMapper.insertSelective(noteDO);
        log.info("插入NoteDO成功, detailId={}", noteDO.getId());

        NoteContentDO noteContentDO = new NoteContentDO();
        noteContentDO.setNoteId(noteId);
        noteContentDO.setContent(noteSubmitDTO.getContent());
        noteContentDOMapper.insertSelective(noteContentDO);
        log.info("插入NoteContentDO成功");

        NoteCountDO noteCountDO = new NoteCountDO();
        noteCountDO.setNoteId(noteId);
        noteCountDOMapper.insertSelective(noteCountDO);
        log.info("插入NoteCountDO成功");

        if (basicInsert > 0 && detailInsert > 0) {
            return noteId;
        }
        throw new IllegalStateException("笔记上传失败：数据库插入异常");
    }
}
