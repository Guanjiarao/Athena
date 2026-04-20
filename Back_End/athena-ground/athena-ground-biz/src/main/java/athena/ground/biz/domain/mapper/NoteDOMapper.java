package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Select;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    NoteDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);

    @Select("SELECT * FROM tb_note WHERE id = #{blogId} AND type = #{type} ")
    NoteDO selectBlogDetail(Long blogId,   Byte type);
}