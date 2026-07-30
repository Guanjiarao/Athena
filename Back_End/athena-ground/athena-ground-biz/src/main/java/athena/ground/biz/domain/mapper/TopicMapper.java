package athena.ground.biz.domain.mapper;

import athena.ground.biz.domain.dataobject.TopicDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TopicMapper {

    List<TopicDO> selectActiveByNames(@Param("topicNames") List<String> topicNames);

    TopicDO selectActiveById(@Param("id") Long id);
}
