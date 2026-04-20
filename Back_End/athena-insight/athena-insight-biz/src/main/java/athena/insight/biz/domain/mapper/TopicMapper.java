package athena.insight.biz.domain.mapper;

import athena.insight.biz.domain.dataobject.TopicDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TopicMapper extends BaseMapper<TopicDO> {
}
