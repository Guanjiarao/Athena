package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteBasicDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface NoteBasicDOMapper {
    int deleteByPrimaryKey(Long noteId);

    int insert(NoteBasicDO record);

    int insertSelective(NoteBasicDO record);

    NoteBasicDO selectByPrimaryKey(Long noteId);

    int updateByPrimaryKeySelective(NoteBasicDO record);

    int updateByPrimaryKey(NoteBasicDO record);

    @Select("SELECT * FROM tb_note_basic WHERE note_id = #{noteId} AND type = #{type}")
    NoteBasicDO selectByNoteIdAndType(@Param("noteId") Long noteId, @Param("type") Byte type);

    @Select("SELECT * FROM tb_note_basic WHERE note_id = #{noteId}")
    NoteBasicDO selectByNoteId(@Param("noteId") Long noteId);

    @Select("SELECT * FROM tb_note_basic WHERE note_id = #{noteId} AND status = 1")
    NoteBasicDO selectApprovedByNoteId(@Param("noteId") Long noteId);

    @Select({"<script>",
            "SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name, create_time, update_time ",
            "FROM tb_note_basic",
            "WHERE note_id IN",
            "<foreach collection='noteIdList' item='noteId' open='(' separator=',' close=')'>",
            "#{noteId}",
            "</foreach>",
            "ORDER BY update_time DESC, note_id DESC",
            "</script>"})
    List<NoteBasicDO> selectByNoteIdList(@Param("noteIdList") List<Long> noteIdList);

    @Select("SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name " +
            "FROM tb_note_basic " +
            "WHERE status = 1 AND (`type` = #{type} OR `type` = #{type} + 1) " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{pageSize}")
    List<NoteBasicDO> selectApprovedByType(@Param("type") Integer type,
                                           @Param("offset") Integer offset,
                                           @Param("pageSize") Integer pageSize);

    @Select("SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name " +
            "FROM tb_note_basic " +
            "WHERE channel_id = #{channelId} AND type != 1 AND type != 2 AND status = 1 " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{pageSize}")
    List<NoteBasicDO> selectApprovedByChannelId(@Param("channelId") Integer channelId,
                                                @Param("offset") Integer offset,
                                                @Param("pageSize") Integer pageSize);

    @Select("SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name, create_time " +
            "FROM tb_note_basic " +
            "WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{pageSize}")
    List<NoteBasicDO> selectByUserId(@Param("userId") Long userId,
                                     @Param("offset") Integer offset,
                                     @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name, create_time, update_time ",
            "FROM tb_note_basic",
            "WHERE status = #{status}",
            "AND title LIKE CONCAT('%', #{keyword}, '%')",
            "<if test='type != null'> AND type = #{type}</if>",
            "<if test='channelId != null'> AND channel_id = #{channelId}</if>",
            "ORDER BY update_time DESC, note_id DESC LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<NoteBasicDO> searchApprovedByTitle(@Param("keyword") String keyword,
                                            @Param("status") Byte status,
                                            @Param("type") Integer type,
                                            @Param("channelId") Integer channelId,
                                            @Param("offset") Integer offset,
                                            @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name, create_time, update_time ",
            "FROM tb_note_basic",
            "WHERE user_id = #{userId}",
            "AND title LIKE CONCAT('%', #{keyword}, '%')",
            "<if test='status != null'> AND status = #{status}</if>",
            "<if test='type != null and type == 1'> AND type IN (1, 2)</if>",
            "<if test='type != null and type != 1'> AND type = #{type}</if>",
            "ORDER BY update_time DESC, note_id DESC LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<NoteBasicDO> searchByUserIdAndTitle(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             @Param("status") Byte status,
                                             @Param("type") Integer type,
                                             @Param("offset") Integer offset,
                                             @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT * FROM tb_note_basic",
            "WHERE status = 0",
            "<if test='type != null'> AND type = #{type}</if>",
            "<if test='channelId != null'> AND channel_id = #{channelId}</if>",
            "ORDER BY create_time ASC LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<NoteBasicDO> selectPendingPage(@Param("type") Byte type,
                                        @Param("channelId") Integer channelId,
                                        @Param("offset") Integer offset,
                                        @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT user_id, note_id, type, status, review_remark, cover_url, title, channel_id, channel_name, create_time, update_time, review_time, reviewer_id",
            "FROM tb_note_basic",
            "WHERE status = 1",
            "AND type IS NOT NULL",
            "AND type NOT IN (0, 1, 2)",
            "<if test='noteId != null'> AND note_id = #{noteId}</if>",
            "<if test='type != null'> AND type = #{type}</if>",
            "ORDER BY review_time ASC, note_id ASC LIMIT #{limit}",
            "</script>"})
    List<NoteBasicDO> selectApprovedRagSyncCandidates(@Param("noteId") Long noteId,
                                                      @Param("type") Byte type,
                                                      @Param("limit") Integer limit);
}
