package athena.insight.biz.domain.mapper;

import athena.insight.biz.domain.dataobject.UserInsightDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInsightMapper extends BaseMapper<UserInsightDO> {
}
