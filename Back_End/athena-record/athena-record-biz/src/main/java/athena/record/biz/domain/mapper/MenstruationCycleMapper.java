package athena.record.biz.domain.mapper;

import athena.record.biz.domain.dataobject.MenstruationCycle;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenstruationCycleMapper extends BaseMapper<MenstruationCycle> {

    @Select("SELECT * FROM menstruation_cycle WHERE user_id = #{userId} AND is_predicted = 0 ORDER BY start_date DESC LIMIT 1")
    MenstruationCycle selectLatestActualCycle(@Param("userId") Long userId);

    @Select("SELECT * FROM menstruation_cycle WHERE user_id = #{userId} AND is_predicted = 0 AND end_date IS NULL ORDER BY start_date DESC LIMIT 1")
    MenstruationCycle selectLatestOpenActualCycle(@Param("userId") Long userId);

    @Select("SELECT * FROM menstruation_cycle WHERE user_id = #{userId} AND is_predicted = 1 ORDER BY start_date DESC LIMIT 1")
    MenstruationCycle selectLatestPredictedCycle(@Param("userId") Long userId);

    @Delete("DELETE FROM menstruation_cycle WHERE user_id = #{userId} AND is_predicted = 1")
    int deletePredictedCycles(@Param("userId") Long userId);
}
